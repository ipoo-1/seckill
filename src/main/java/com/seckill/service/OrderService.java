package com.seckill.service;

public interface OrderService {
    // 创建订单（由 MQ 消费者调用）
    void createOrder(Long userId, Long productId);
}