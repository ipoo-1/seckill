package com.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.dto.SeckillOrderMessage;
import com.seckill.entity.SeckillOrderOutbox;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SeckillOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SeckillOutboxDispatcher.class);

    private final SeckillOutboxService outboxService;
    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int batchSize;

    public SeckillOutboxDispatcher(SeckillOutboxService outboxService,
                                   DefaultMQProducer producer,
                                   ObjectMapper objectMapper,
                                   @Value("${rocketmq.order-topic:seckill-order}") String topic,
                                   @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.outboxService = outboxService;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay-ms:1000}")
    public void dispatch() {
        for (SeckillOrderOutbox outbox : outboxService.findDispatchable(batchSize)) {
            try {
                SeckillOrderMessage payload = new SeckillOrderMessage(
                        outbox.getId(),
                        outbox.getOrderNo(),
                        outbox.getUserId(),
                        outbox.getProductId());
                Message message = new Message(
                        topic,
                        objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
                message.setKeys(outbox.getOrderNo());
                producer.send(message);
                outboxService.markSent(outbox.getId());
            } catch (Exception e) {
                log.warn("outbox_dispatch_failed outboxId={} error={}", outbox.getId(), e.getMessage());
                outboxService.markRetry(outbox.getId(), e);
            }
        }
    }
}
