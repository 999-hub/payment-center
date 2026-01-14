package com.wzy.paymentcenter.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PayNotifyRes {
    private boolean success;
    private String res;
    public static PayNotifyRes ok() {
        return new PayNotifyRes(true, "success");
    }
    public static PayNotifyRes fail(String msg) {
        // 有些支付端要求固定返回 "FAIL" 或 "error"
        return new PayNotifyRes(false, "FAIL");
    }
}
