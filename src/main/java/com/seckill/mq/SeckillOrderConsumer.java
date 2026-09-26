package com.seckill.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.dto.SeckillOrderMessage;
import com.seckill.service.OrderService;
import com.seckill.service.SeckillOutboxService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Component
public class SeckillOrderConsumer {

    private final OrderService orderService;
    private final SeckillOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final String nameServer;
    private final String consumerGroup;
    private final String topic;

    public SeckillOrderConsumer(OrderService orderService,
                                SeckillOutboxService outboxService,
                                ObjectMapper objectMapper,
                                @Value("${rocketmq.name-server:127.0.0.1:9876}") String nameServer,
                                @Value("${rocketmq.consumer-group:seckill-consumer-group}") String consumerGroup,
                                @Value("${rocketmq.order-topic:seckill-order}") String topic) {
        this.orderService = orderService;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.nameServer = nameServer;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
    }

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        // 从最新消息开始消费（不处理历史消息）
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    SeckillOrderMessage message = objectMapper.readValue(body, SeckillOrderMessage.class);
                    orderService.createOrder(
                            message.outboxId(),
                            message.userId(),
                            message.productId(),
                            message.orderNo());
                    outboxService.markCompleted(message.outboxId());
                } catch (Exception e) {
                    try {
                        JsonNode node = objectMapper.readTree(new String(msg.getBody(), StandardCharsets.UTF_8));
                        Long outboxId = node.path("outboxId").asLong();
                        if (outboxId > 0) {
                            outboxService.markRetry(outboxId, e);
                        }
                    } catch (Exception ignored) {
                        // 消息格式错误时只记录日志，避免无限重试毒消息
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
