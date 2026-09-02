package com.seckill.service.impl;

import com.seckill.common.BusinessException;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.Semaphore;

@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // JUC：信号量限流，同一时刻最多 10 个请求进入核心逻辑
    private final Semaphore seckillSemaphore = new Semaphore(10);

    // Redis Lua 脚本：原子扣减库存
    // 返回 -1：库存不存在；0：库存不足；1：扣减成功
    private static final String DECR_STOCK_SCRIPT =
            "local stock = redis.call('get', KEYS[1]) " +
                    "if not stock then return -1 end " +
                    "if tonumber(stock) < tonumber(ARGV[1]) then return 0 end " +
                    "redis.call('decrby', KEYS[1], ARGV[1]) " +
                    "return 1";

    private final DefaultRedisScript<Long> decrStockRedisScript =
            new DefaultRedisScript<>(DECR_STOCK_SCRIPT, Long.class);

    @Override
    public void prepareStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        // 把 MySQL 的库存预热到 Redis（key: seckill:stock:商品id）
        stringRedisTemplate.opsForValue()
                .set("seckill:stock:" + productId, String.valueOf(product.getStock()));
    }
    @Autowired
    private DefaultMQProducer rocketMQProducer;

    @Override
    public void seckill(Long userId, Long productId) {
        // 1. JUC 信号量限流：抢不到名额直接拒绝
        if (!seckillSemaphore.tryAcquire()) {
            throw new BusinessException("当前人数过多，请稍后再试");
        }
        try {
            // 2. 防重复抢购：每个用户对每个商品只能抢一次
            //    SADD 返回 1=加入成功(第一次)，0=已存在(重复)
            Long added = stringRedisTemplate.opsForSet()
                    .add("seckill:user:" + userId, productId.toString());
            if (added != null && added == 0L) {
                throw new BusinessException("您已抢购过该商品，不能重复抢购");
            }

            // 3. Lua 原子扣减库存（核心！防超卖）
            Long result = stringRedisTemplate.execute(
                    decrStockRedisScript,
                    Collections.singletonList("seckill:stock:" + productId),
                    "1");

            if (result == null || result != 1L) {
                // 扣减失败：撤销刚才的"已抢购"标记（回滚）
                stringRedisTemplate.opsForSet()
                        .remove("seckill:user:" + userId, productId.toString());
                throw new BusinessException("手慢了，商品已抢光");
            }
            // 4. 扣减成功 → 发消息，让订单系统异步创建订单（削峰填谷）
            try {
                String body = "{\"userId\":" + userId + ",\"productId\":" + productId + "}";
                Message message = new Message("seckill-order", body.getBytes(StandardCharsets.UTF_8));
                rocketMQProducer.send(message);
            } catch (Exception e) {
                throw new BusinessException("系统繁忙，请稍后再试");
            }
        } finally {
            seckillSemaphore.release();
        }
    }
}