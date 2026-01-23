package com.wzy.paymentcenter.mapper;

import com.wzy.paymentcenter.domain.entity.PaymentDO;
import org.apache.ibatis.annotations.Param;


//一般项目中mapper层不写@Mapper,需要在启动类中添加@MapperScan("com.wzy.paymentcenter.mapper")
public interface PaymentMapper {
    PaymentDO selectByPayNoForUpdate(String orderNo);

    int updateToPaid(@Param("id") Long id, @Param("channelTradeNo") String channelTradeNo);

    int updateToClosed(Long id);
}

