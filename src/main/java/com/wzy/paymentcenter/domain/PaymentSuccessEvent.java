package com.wzy.paymentcenter.domain;

import lombok.Data;

@Data
public class PaymentSuccessEvent {
    private String eventId;
    private String orderNo;
    private Long amount;
}
