package com.wzy.paymentcenter.iface;

import com.wzy.paymentcenter.domain.PayNotifyRes;


public interface ICallBack {
    PayNotifyRes handlePayNotify(PayNotifyReq request);
}
