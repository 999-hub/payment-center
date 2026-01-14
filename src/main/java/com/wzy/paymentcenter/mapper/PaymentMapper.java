package com.wzy.paymentcenter.mapper;

import com.wzy.paymentcenter.domain.entity.PaymentDO;

public interface PaymentMapper {
    PaymentDO selectByPayNoForUpdate(String payNo);

    int updateToPaid(Long id, String channelTradeNo, Long paidAt, Long amount);

    int updateToFailed(Long id, String channelStatus);
}

