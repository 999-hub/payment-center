package com.wzy.paymentcenter.ruler;

import com.alipay.api.internal.util.AlipaySignature;
import com.wzy.paymentcenter.constans.PayChannel;
import com.wzy.paymentcenter.constans.AliPayStatus;
import com.wzy.paymentcenter.domain.req.PayNotifyReq;
import com.wzy.paymentcenter.service.PayResultCallBackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 支付宝异步通知回调 Controller
 *
 * 说明：
 * 1) 支付宝会以 form 参数方式 POST 到 notify_url（不是 JSON）
 * 2) 你必须先验签，再做业务处理
 * 3) 处理成功必须返回字符串 "success"，否则支付宝会重试通知
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/notify")
public class PayCallBack {

    private final PayResultCallBackService payNotifyService;

    @Value("${pay.alipay.public-key}")
    private String alipayPublicKey;

    @Value("${pay.alipay.charset:UTF-8}")
    private String charset;

    @Value("${pay.alipay.sign-type:RSA2}")
    private String signType;

    private static final DateTimeFormatter ALIPAY_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 支付宝异步回调入口
     * URL 示例：/notify/alipay
     */
    @PostMapping(value = "/alipay")
    public String alipayNotify(Map<String, String> params) {
        //Map<String, String> params = extractAlipayParams(request);

        // 0) 基础日志（注意：生产环境别把所有敏感字段完整打印）
        log.info("Alipay notify received. out_trade_no={}, trade_no={}, trade_status={}",
                params.get("out_trade_no"), params.get("trade_no"), params.get("trade_status"));

        // 1) 验签（强制）
        /*boolean signOk = verifyAlipaySign(params);
        if (!signOk) {
            log.warn("Alipay notify sign verify FAILED. out_trade_no={}, notify_id={}",
                    params.get("out_trade_no"), params.get("notify_id"));
            return "failure";
        }*/

        // 2) 转成内部统一模型（融合处理）
        PayNotifyReq unified = mapToUnified(params);

        // 3) 进入统一业务处理（幂等、金额校验、状态机更新、发消息等都放这里）
        try {
            payNotifyService.handlePayNotify(unified);
            // 4) 内部代码都执行完,没有catch错误,必须返回 success，否则支付宝会重试
            return "success";
        } catch (Exception e) {
            // 业务处理失败建议返回 failure 让支付宝重试（或者你做幂等后也可以返回 success + 内部异步补偿）
            log.error("Alipay notify handle failed. out_trade_no={}, trade_no={}", e);
            return "failure";//返回 failure之后,支付宝会自动重试
        }
    }

    /**
     * 从 HttpServletRequest 提取支付宝回调参数
     * 注意：支付宝参数可能有多值（通常不会，但写成通用更稳）
     */
    private Map<String, String> extractAlipayParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>(64);
        Map<String, String[]> requestParams = request.getParameterMap();

        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            String name = entry.getKey();
            String[] values = entry.getValue();

            if (values == null) continue;

            if (values.length == 1) {
                params.put(name, values[0]);
            } else {
                // 多值拼接（支付宝一般不会多值，这里防御性处理）
                params.put(name, String.join(",", values));
            }
        }
        return params;
    }

    /**
     * 支付宝验签
     */
    private boolean verifyAlipaySign(Map<String, String> params) {
        try {
            // AlipaySignature.rsaCheckV1 会自动忽略 sign/sign_type 参与验签拼串
            return AlipaySignature.rsaCheckV1(params, alipayPublicKey, charset, signType);
        } catch (Exception e) {
            log.error("Alipay sign verify exception.", e);
            return false;
        }
    }

    /**
     * 支付宝回调 → 统一模型
     * 这里做字段映射与标准化：
     * - 金额统一为“分”
     * - 状态统一为你自己的枚举
     * - 时间统一为时间戳
     */
    private PayNotifyReq mapToUnified(Map<String, String> p) {
        String tradeStatus = p.get("trade_status");
        AliPayStatus status = mapAlipayStatus(tradeStatus);

        long amount = yuanToFenSafe(p.get("total_amount")); // 支付宝是元
        long paidAt = parseAlipayTimeToEpoch(p.get("gmt_payment"));

        return PayNotifyReq.builder()
                .orderNo(p.get("out_trade_no"))
                .channel(PayChannel.ALIPAY)
                .channelTradeNo(p.get("trade_no"))
                .status(status)
                .sellerId(p.get("seller_id"))
                .amount(amount)
                .appId(p.get("app_id"))
                .currency("CNY")
                .paidAt(paidAt)
                .rawParams(new HashMap<>(p)) // 留存原始参数，便于审计/排障
                .build();
    }

    private AliPayStatus mapAlipayStatus(String tradeStatus) {
        if (!StringUtils.hasText(tradeStatus)) return AliPayStatus.UNKNOWN;

        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return AliPayStatus.SUCCESS;
            case "TRADE_CLOSED":
                return AliPayStatus.CLOSED;
            case "WAIT_BUYER_PAY":
                return AliPayStatus.PENDING;
            default:
                return AliPayStatus.UNKNOWN;
        }
    }

    private long yuanToFenSafe(String yuan) {
        if (!StringUtils.hasText(yuan)) return 0L;
        // 避免浮点误差：使用 BigDecimal
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(yuan);
            return bd.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (Exception e) {
            log.warn("yuanToFen parse failed: {}", yuan, e);
            return 0L;
        }
    }

    private long parseAlipayTimeToEpoch(String dt) {
        // gmt_payment 可能为空（未支付/关闭等）
        if (!StringUtils.hasText(dt)) return 0L;
        try {
            LocalDateTime ldt = LocalDateTime.parse(dt, ALIPAY_DT);
            // 按你系统时区取（一般中国业务用 Asia/Shanghai）
            return ldt.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.warn("parseAlipayTimeToEpoch failed: {}", dt, e);
            return 0L;
        }
    }
}

