package com.wzy.paymentcenter.constans;

/**
 * 统一支付状态（内部标准态）
 */
public enum AliPayStatus {

    /** 支付成功（终态） */
    SUCCESS,

    /** 支付中 / 待支付 */
    PENDING,

    /** 已关闭 / 已取消 / 已退款（终态） */
    CLOSED,

    /** 未知状态 */
    UNKNOWN
}
