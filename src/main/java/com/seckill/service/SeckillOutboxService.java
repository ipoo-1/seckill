package com.seckill.service;

import com.seckill.entity.SeckillOrderOutbox;

import java.util.List;

public interface SeckillOutboxService {

    void createPending(String orderNo, Long userId, Long productId);

    List<SeckillOrderOutbox> findDispatchable(int batchSize);

    void markSent(Long outboxId);

    void markCompleted(Long outboxId);

    void markRetry(Long outboxId, Exception error);

    void markFailed(Long outboxId, Exception error);
}
