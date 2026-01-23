package com.wzy.paymentcenter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzy.paymentcenter.constans.AliPayStatus;
import com.wzy.paymentcenter.domain.PaymentSuccessEvent;
import com.wzy.paymentcenter.domain.entity.OutboxEventDO;
import com.wzy.paymentcenter.domain.entity.PaymentDO;
import com.wzy.paymentcenter.domain.req.PayNotifyReq;
import com.wzy.paymentcenter.iface.ICallBack;
import com.wzy.paymentcenter.mapper.OutboxEventMapper;
import com.wzy.paymentcenter.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayResultCallBackService implements ICallBack {
    private final int DB_PAY_SUCCESS = 1;
    private final int DB_PAY_CLOSED = 2;
    private final String TOPIC_PAYMENT_SUCCESS = "viplounge";

    private final PaymentMapper paymentMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayNotify(PayNotifyReq req) {
        // 2.1 select ... for update 行级锁，防止并发回调重复处理
        PaymentDO payment = paymentMapper.selectByPayNoForUpdate(req.getOrderNo());
        if (payment == null) {
            log.error("payment not found, orderNo={}", req.getOrderNo());
            throw new IllegalStateException("payment not found, payNo=" + req.getOrderNo());
        }

        /*// 2.2 关键字段校验(支付宝中写的,必须检验的四段)
        String outTradeNo = req.getPayNo();
        String totalAmount =
        String sellerId =

        if (!StringUtils.hasText(outTradeNo) || !StringUtils.hasText(totalAmount) || !StringUtils.hasText(tradeStatus)) {
        log.warn("Alipay notify missing required fields. out_trade_no={}, trade_no={}, trade_status={}",
                outTradeNo, totalAmount, tradeStatus);
        return "failure";}*/

        // 2.1.1 幂等：如果已经支付成功，直接忽略重复回调
        if (payment.getStatus() == DB_PAY_SUCCESS) {
            log.info("【幂等拦截】订单已支付，直接返回成功。orderNo={}", req.getOrderNo());
            // 直接 return，方法正常结束，事务正常提交（虽然没改数据），上层返回 success
            return;
        }

        // 2.2 更新支付中心 Payment 表状态（未支付 -> 已支付）
        if (req.getStatus() == AliPayStatus.SUCCESS) {
            // 2.2.1 支付成功
            int updated = paymentMapper.updateToPaid(
                    payment.getId(),
                    req.getChannelTradeNo()
            );
            if (updated != 1) {
                // 乐观兜底：如果更新行数不是1，说明状态可能被并发修改，直接抛异常让上层FAIL重试
                log.error("update payment to PAID failed, payNo={}", req.getPayNo());
                throw new IllegalStateException("update payment to PAID failed, payNo=" + req.getPayNo());
            }
        } else {
            // 2.2.2 交易关闭
            // 失败/关闭等状态，更新为 CLOSED
            int updated = paymentMapper.updateToClosed(payment.getId());
            if (updated != 1) {
                throw new IllegalStateException("update payment to FAIL failed, payNo=" + req.getPayNo());
            }
        }

        /**
         * 事务内做：select for update（行级锁）+ 幂等判断 + 更新支付单状态
         */

        // 2) 组装“支付成功事件”
        PaymentSuccessEvent event = new PaymentSuccessEvent();
        event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        event.setOrderNo(req.getOrderNo());
        event.setAmount(req.getAmount());

        // 3) 写 Outbox（同一个事务）
        OutboxEventDO out = new OutboxEventDO();
        out.setEventId(event.getEventId());
        out.setTopic(TOPIC_PAYMENT_SUCCESS);
        out.setEventType("viplounge");
        out.setAggregateId(req.getOrderNo());
        out.setPayload(toJson(event));

        out.setStatus("NEW");
        out.setRetryCount(0);
        out.setCreatedAt(LocalDateTime.now());
        out.setUpdatedAt(LocalDateTime.now());

        outboxEventMapper.insert(out);

        // 4) 不发 MQ：让 worker 去发，避免“DB提交成功但MQ发送失败”*/
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException("serialize outbox payload failed", e);
        }
    }
}



