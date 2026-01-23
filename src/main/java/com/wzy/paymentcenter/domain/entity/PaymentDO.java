package com.wzy.paymentcenter.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentDO {
    //主键
    private Long id;
    //订单号
    private String orderNo;
    //支付类型
    private String payType;
    //支付渠道
    private String channel;
    //渠道流水号
    private String serialNo;
    //总价
    private Long amount;
    //支付状态
    private int status;
    //订单创建时间
    private LocalDateTime createTime;
}
