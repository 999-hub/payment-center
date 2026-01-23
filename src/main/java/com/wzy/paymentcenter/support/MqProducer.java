package com.wzy.paymentcenter.support;

import com.wzy.paymentcenter.domain.PaymentSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class MqProducer {
    public void sendPaymentSuccess(PaymentSuccessEvent event) {

    }
}
