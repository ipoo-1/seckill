package com.seckill.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.service.OrderService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Component
public class SeckillOrderConsumer {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws Exception {
        consumer = new DefaultMQPushConsumer("seckill-consumer-group");
        consumer.setNamesrvAddr("localhost:9876");
        // 从最新消息开始消费（不处理历史消息）
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe("seckill-order", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    JsonNode node = objectMapper.readTree(body);
                    Long userId = node.get("userId").asLong();
                    Long productId = node.get("productId").asLong();
                    orderService.createOrder(userId, productId);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 消费失败：返回重试，RocketMQ 会自动重发
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
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