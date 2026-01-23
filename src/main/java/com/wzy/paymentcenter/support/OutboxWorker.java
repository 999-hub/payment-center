package com.wzy.paymentcenter.support;

import com.wzy.paymentcenter.domain.entity.OutboxEventDO;
import com.wzy.paymentcenter.mapper.OutboxEventMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class OutboxWorker {

    private final OutboxEventMapper outboxEventMapper;
    private final KafkaProducer kafkaProducer;

    public OutboxWorker(OutboxEventMapper outboxEventMapper, KafkaProducer kafkaProducer) {
        this.outboxEventMapper = outboxEventMapper;
        this.kafkaProducer = kafkaProducer;
    }

    @Scheduled(fixedDelay = 1000L)
    public void pollAndPublish() {
        List<OutboxEventDO> batch = claimAndMarkSending(200);
        if (batch == null || batch.isEmpty()) {
            return;
        }

        for (OutboxEventDO e : batch) {
            try {
                // key建议用 aggregateId(orderNo) 保证同订单顺序
                kafkaProducer.send(e.getTopic(), e.getAggregateId(), e.getPayload());
                outboxEventMapper.markSent(e.getId(), LocalDateTime.now());
            } catch (Exception ex) {
                int retry = (e.getRetryCount() == null ? 0 : e.getRetryCount()) + 1;//+1是在判断外面的
                LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds(retry));
                outboxEventMapper.markFailed(e.getId(), retry, nextRetryAt, LocalDateTime.now());
            }
        }
    }

    /**
     * 从数据库里抢占一批可发送的outvox事件,加行级锁防止并发重复, 并标记为SENDING,交给kafka去发送
     * 事务内：锁一批行（SKIP LOCKED）-> 标记 SENDING -> 返回这批记录
     */
    @Transactional
    public List<OutboxEventDO> claimAndMarkSending(int limit) {
        LocalDateTime now = LocalDateTime.now();
        //1.抢占
        List<OutboxEventDO> locked = outboxEventMapper.selectForUpdateSkipLocked(now, limit);
        if (locked == null || locked.isEmpty()) {
            return locked;
        }
        //1.2把id收集起来
        List<Long> ids = new ArrayList<Long>(locked.size());
        for (OutboxEventDO e : locked) {
            ids.add(e.getId());
        }
        //2.标记
        outboxEventMapper.markSendingBatch(ids, now);
        return locked;
    }

    private long backoffSeconds(int retry) {
        // 1,2,4,8,16,32,64 -> cap 60
        int shift = retry - 1;
        if (shift < 0) shift = 0;
        if (shift > 6) shift = 6;
        long s = 1L << shift;//1往左移shift位
        return Math.min(s, 60L);
    }
}
