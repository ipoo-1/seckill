package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.entity.SeckillOrderOutbox;
import com.seckill.mapper.SeckillOrderOutboxMapper;
import com.seckill.service.SeckillOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeckillOutboxServiceImpl implements SeckillOutboxService {

    private static final String PENDING = "PENDING";
    private static final String SENT = "SENT";
    private static final String COMPLETED = "COMPLETED";
    private static final String FAILED = "FAILED";

    private final SeckillOrderOutboxMapper outboxMapper;
    private final int maxRetry;
    private final Duration republishAfter;

    public SeckillOutboxServiceImpl(SeckillOrderOutboxMapper outboxMapper,
                                    @Value("${app.outbox.max-retry:8}") int maxRetry,
                                    @Value("${app.outbox.republish-after-seconds:300}") long republishAfterSeconds) {
        this.outboxMapper = outboxMapper;
        this.maxRetry = Math.max(1, maxRetry);
        this.republishAfter = Duration.ofSeconds(Math.max(30, republishAfterSeconds));
    }

    @Override
    @Transactional
    public void createPending(String orderNo, Long userId, Long productId) {
        LocalDateTime now = LocalDateTime.now();
        SeckillOrderOutbox outbox = new SeckillOrderOutbox();
        outbox.setOrderNo(orderNo);
        outbox.setUserId(userId);
        outbox.setProductId(productId);
        outbox.setStatus(PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxMapper.insert(outbox);
    }

    @Override
    public List<SeckillOrderOutbox> findDispatchable(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime republishBefore = now.minus(republishAfter);
        return outboxMapper.selectList(new LambdaQueryWrapper<SeckillOrderOutbox>()
                .and(wrapper -> wrapper
                        .eq(SeckillOrderOutbox::getStatus, PENDING)
                        .le(SeckillOrderOutbox::getNextRetryAt, now)
                        .or()
                        .eq(SeckillOrderOutbox::getStatus, SENT)
                        .le(SeckillOrderOutbox::getSentAt, republishBefore))
                .orderByAsc(SeckillOrderOutbox::getId)
                .last("LIMIT " + Math.max(1, batchSize)));
    }

    @Override
    public void markSent(Long outboxId) {
        SeckillOrderOutbox update = new SeckillOrderOutbox();
        update.setId(outboxId);
        update.setStatus(SENT);
        update.setSentAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        update.setLastError(null);
        outboxMapper.updateById(update);
    }

    @Override
    public void markCompleted(Long outboxId) {
        SeckillOrderOutbox update = new SeckillOrderOutbox();
        update.setId(outboxId);
        update.setStatus(COMPLETED);
        update.setCompletedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        update.setLastError(null);
        outboxMapper.updateById(update);
    }

    @Override
    public void markRetry(Long outboxId, Exception error) {
        SeckillOrderOutbox current = outboxMapper.selectById(outboxId);
        if (current == null) {
            return;
        }
        int retryCount = current.getRetryCount() == null ? 1 : current.getRetryCount() + 1;
        if (retryCount > maxRetry) {
            markFailed(outboxId, error);
            return;
        }

        long delaySeconds = Math.min(60, 1L << Math.min(retryCount, 6));
        SeckillOrderOutbox update = new SeckillOrderOutbox();
        update.setId(outboxId);
        update.setStatus(PENDING);
        update.setRetryCount(retryCount);
        update.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        update.setUpdatedAt(LocalDateTime.now());
        update.setLastError(truncate(error));
        outboxMapper.updateById(update);
    }

    @Override
    public void markFailed(Long outboxId, Exception error) {
        SeckillOrderOutbox update = new SeckillOrderOutbox();
        update.setId(outboxId);
        update.setStatus(FAILED);
        update.setUpdatedAt(LocalDateTime.now());
        update.setLastError(truncate(error));
        outboxMapper.updateById(update);
    }

    private String truncate(Exception error) {
        String message = error == null || error.getMessage() == null
                ? "unknown error"
                : error.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
