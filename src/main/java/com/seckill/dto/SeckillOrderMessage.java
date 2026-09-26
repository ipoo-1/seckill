package com.seckill.dto;

public record SeckillOrderMessage(
        Long outboxId,
        String orderNo,
        Long userId,
        Long productId
) {
}
