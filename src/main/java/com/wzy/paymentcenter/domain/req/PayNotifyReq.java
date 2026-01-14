package com.wzy.paymentcenter.domain.req;

import lombok.Data;

@Data
public class PayNotifyReq {
    private String orderNo;      // 业务订单号
    private String payNo;        // 支付单号（支付中心生成）
    private String channelTradeNo; // 渠道流水号
    private String status;       // SUCCESS/FAIL/CLOSED...
    private Long amount;         // 支付金额（分）
    private String currency;     // CNY
    private Long paidAt;         // 支付完成时间戳
    private Long timestamp;      // 回调时间戳（防重放）
    private String nonce;        // 随机串
    private String sign;         // HMAC签名
    private String rawBody;      // 原始报文（有些渠道需要用原文验签，可选）
}
