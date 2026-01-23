package com.wzy.paymentcenter.mapper;
import com.wzy.paymentcenter.domain.entity.OutboxEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper {

    int insert(OutboxEventDO e);

    /**
     * 抢占一批待发送事件（行锁 + SKIP LOCKED）
     * 挑选出一批新加入(NEW)的事件,无重试时间或发送失败(FAILED),但重试时间已到的事件,并且他们都没有被其他线程锁住
     * 注意：必须在事务中调用！
     */
    List<OutboxEventDO> selectForUpdateSkipLocked(@Param("now") LocalDateTime now,
                                                  @Param("limit") int limit);

    /*
    * 更新状态为待处理的事件,并且id需要在ids集合中,更新状态为发送中(SENDING)和更新事件为现在
    * */
    int markSendingBatch(@Param("ids") List<Long> ids,
                         @Param("now") LocalDateTime now);
    /*
    * 把id和SENDING状态对应的事件,更新状态和更新时间*/
    int markSent(@Param("id") Long id,
                 @Param("now") LocalDateTime now);

    int markFailed(@Param("id") Long id,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("now") LocalDateTime now);
}
