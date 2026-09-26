package com.seckill.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMqConfig {

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer defaultMQProducer(
            @Value("${rocketmq.name-server:127.0.0.1:9876}") String nameServer,
            @Value("${rocketmq.producer-group:seckill-producer-group}") String producerGroup) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.start();
        return producer;
    }
}
