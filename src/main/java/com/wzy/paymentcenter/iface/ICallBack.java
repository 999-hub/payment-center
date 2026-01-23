package com.wzy.paymentcenter.iface;

import com.wzy.paymentcenter.domain.req.PayNotifyReq;


public interface ICallBack {
    void handlePayNotify(PayNotifyReq request);
}
