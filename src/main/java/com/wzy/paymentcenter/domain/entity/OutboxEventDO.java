package com.wzy.paymentcenter.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OutboxEventDO {
    private Long id;
    private String eventId;
    private String topic;
    private String eventType;
    private String aggregateId;
    private String payload;

    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime sentAt;

    // getter/setter 省略（Java8可用 Lombok，但这里按纯Java写也行）
}

