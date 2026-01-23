package com.wzy.paymentcenter.domain.req;

import com.wzy.paymentcenter.constans.PayChannel;
import com.wzy.paymentcenter.constans.AliPayStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class PayNotifyReq {
    private String orderNo;      // 业务订单号
    private String payNo;        // 支付单号（支付中心生成）
    private String channelTradeNo; // 渠道流水号
    private AliPayStatus status;       // SUCCESS/FAIL/CLOSED...
    private String sellerId;
    private PayChannel channel;
    private Long amount;         // 支付金额（分）
    private String appId;
    private String currency;     // CNY
    private Long paidAt;         // 支付完成时间戳
    private Long timestamp;      // 回调时间戳（防重放
    private Map<String, String> rawParams;
}
