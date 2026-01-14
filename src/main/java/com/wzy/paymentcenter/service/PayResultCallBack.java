package com.wzy.paymentcenter.service;

import com.wzy.paymentcenter.domain.PayNotifyRes;
import com.wzy.paymentcenter.domain.req.PayNotifyReq;
import com.wzy.paymentcenter.iface.ICallBack;
import com.wzy.paymentcenter.mapper.PaymentMapper;
import org.springframework.transaction.annotation.Transactional;


public class PayResultCallBack implements ICallBack {
    private final SignVerifier signVerifier;
    private final PaymentMapper paymentMapper;
    private final MqProducer mqProducer;

    @Override
    public PayNotifyRes handlePayNotify(PayNotifyReq req) {
        // 1. 验签（HMAC）
        if (!signVerifier.verify(req)) {
            log.warn("pay notify verify failed, orderNo={}, payNo={}", req.getOrderNo(), req.getPayNo());
            // 通常验签失败要返回 FAIL，让支付端重试 or 人工介入（看渠道协议）
            return PayNotifyRes.fail("invalid sign");
        }

        try {
            // 2. 开事务 + 幂等更新 + 发送MQ
            processPayNotifyInTx(req);
            // 3. 返回 ACK（告诉支付端你已处理）
            return PayNotifyRes.ok();
        } catch (Exception e) {
            // 注意：这里建议返回 FAIL 让支付端重试（否则你这边失败了却告诉对方成功，会丢通知）
            log.error("process pay notify failed, orderNo={}, payNo={}", req.getOrderNo(), req.getPayNo(), e);
            return PayNotifyRes.fail("internal error");
        }
    }
    /**
     * 事务内做：select for update（行级锁）+ 幂等判断 + 更新支付单状态
     * 事务提交后：发MQ（更安全的方式是 outbox，这里先用简化写法）
     */
    @Transactional(rollbackFor = Exception.class)
    protected void processPayNotifyInTx(PayNotifyReq req) {
        // 2.1 select ... for update 行级锁，防止并发回调重复处理
        PaymentDO payment = paymentMapper.selectByPayNoForUpdate(req.getPayNo());
        if (payment == null) {
            throw new IllegalStateException("payment not found, payNo=" + req.getPayNo());
        }

        // 2.1.1 幂等：如果已经成功/退款成功，直接忽略重复回调
        if (Objects.equals(payment.getStatus(), "PAID")) {
            log.info("duplicate notify ignored(pay already PAID), payNo={}", req.getPayNo());
            return;
        }

        // 2.2 更新支付中心 Payment 表状态（未支付 -> 已支付）
        if ("SUCCESS".equalsIgnoreCase(req.getStatus())) {
            int updated = paymentMapper.updateToPaid(
                    payment.getId(),
                    req.getChannelTradeNo(),
                    req.getPaidAt(),
                    req.getAmount()
            );
            if (updated != 1) {
                // 乐观兜底：如果更新行数不是1，说明状态可能被并发修改，直接抛异常让上层FAIL重试
                throw new IllegalStateException("update payment to PAID failed, payNo=" + req.getPayNo());
            }
        } else {
            // 失败/关闭等状态，按你的业务决定要不要更新为 FAIL/CLOSED
            int updated = paymentMapper.updateToFailed(payment.getId(), req.getStatus());
            if (updated != 1) {
                throw new IllegalStateException("update payment to FAIL failed, payNo=" + req.getPayNo());
            }
            return;
        }

        // 2.3 发消息给业务方（贵宾厅服务），让它去发券/更新订单/短信等
        // 强烈建议：支付中心只发“支付成功事件”，业务方自己消费做业务动作
        PaymentSuccessEvent event = new PaymentSuccessEvent();
        event.setOrderNo(req.getOrderNo());
        event.setPayNo(req.getPayNo());
        event.setAmount(req.getAmount());
        event.setChannelTradeNo(req.getChannelTradeNo());
        event.setPaidAt(req.getPaidAt());

        mqProducer.sendPaymentSuccess(event);
        // 注意：更稳的是 outbox（先落库event，再由worker发MQ），避免“DB提交成功但MQ发送失败”
    }
}
