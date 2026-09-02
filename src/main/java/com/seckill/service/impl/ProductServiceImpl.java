package com.seckill.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.BusinessException;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Random;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<Product> listProducts() {
        return productMapper.selectList(null);
    }

    @Override
    public Product getProductById(Long id) {
        String cacheKey = "product:" + id;

        // 最多重试 3 次（应对缓存击穿）
        for (int i = 0; i < 3; i++) {
            // 1. 先查 Redis 缓存
            Product cached = getFromCache(cacheKey);
            if (cached != null) {
                return cached;
            }

            // 2. 抢"互斥锁"：只有抢到的线程才去查数据库（防击穿）
            String lockKey = "lock:product:" + id;
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
            if (Boolean.TRUE.equals(locked)) {
                try {
                    // 3. 抢到锁后双检一次缓存（防止重复查库）
                    cached = getFromCache(cacheKey);
                    if (cached != null) {
                        return cached;
                    }
                    // 4. 查数据库 + 写缓存
                    return loadAndCache(cacheKey, id);
                } finally {
                    // 5. 释放锁
                    stringRedisTemplate.delete(lockKey);
                }
            }

            // 没抢到锁：说明别人正在查库，等 100 毫秒再重试
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 极端情况兜底：直接查库
        return loadAndCache(cacheKey, id);
    }

    // 查缓存：返回 null 表示没有缓存
    private Product getFromCache(String cacheKey) {
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json == null) {
            return null;
        }
        if ("null".equals(json)) {
            throw new BusinessException("商品不存在"); // 缓存了"空值"（防穿透）
        }
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (JsonProcessingException e) {
            return null; // 缓存数据异常，当作没缓存
        }
    }

    // 查数据库并写缓存
    private Product loadAndCache(String cacheKey, Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            // 防穿透：把"商品不存在"也缓存 3 分钟
            stringRedisTemplate.opsForValue().set(cacheKey, "null", Duration.ofMinutes(3));
            throw new BusinessException("商品不存在");
        }
        // 防雪崩：过期时间 5 分钟 + 随机 0~2 分钟，避免商品集体同时过期
        long ttl = 300 + new Random().nextInt(120);
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(product), Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("商品序列化失败", e);
        }
        return product;
    }
}