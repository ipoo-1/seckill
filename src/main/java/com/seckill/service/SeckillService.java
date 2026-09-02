package com.seckill.service;

public interface SeckillService {
    // 预热：把 MySQL 库存同步到 Redis
    void prepareStock(Long productId);

    // 秒杀抢购：成功返回，失败抛业务异常
    void seckill(Long userId, Long productId);
}