package com.seckill.service.impl;

import com.seckill.common.BusinessException;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.SeckillOutboxService;
import com.seckill.service.SeckillService;
import com.seckill.util.OrderNoGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SeckillServiceImpl implements SeckillService {

    private static final String RESERVE_STOCK_SCRIPT =
            "local stock = redis.call('get', KEYS[1]) " +
                    "if not stock then return -1 end " +
                    "if tonumber(stock) < tonumber(ARGV[2]) then return 0 end " +
                    "local added = redis.call('sadd', KEYS[2], ARGV[1]) " +
                    "if added == 0 then return 2 end " +
                    "redis.call('expire', KEYS[2], ARGV[3]) " +
                    "redis.call('decrby', KEYS[1], ARGV[2]) " +
                    "return 1";

    private static final String COMPENSATE_STOCK_SCRIPT =
            "if redis.call('srem', KEYS[2], ARGV[1]) == 1 then " +
                    "redis.call('incrby', KEYS[1], ARGV[2]) " +
                    "return 1 " +
                    "end " +
                    "return 0";

    private static final String RATE_LIMIT_SCRIPT =
            "local count = redis.call('incr', KEYS[1]) " +
                    "if count == 1 then redis.call('expire', KEYS[1], ARGV[1]) end " +
                    "if count > tonumber(ARGV[2]) then return 0 end " +
                    "return 1";

    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOutboxService outboxService;
    private final int rateLimitPerSecond;
    private final long reservationTtlSeconds;
    private final long stockTtlSeconds;

    private final DefaultRedisScript<Long> reserveStockScript =
            new DefaultRedisScript<>(RESERVE_STOCK_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> compensateStockScript =
            new DefaultRedisScript<>(COMPENSATE_STOCK_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> rateLimitScript =
            new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    public SeckillServiceImpl(ProductMapper productMapper,
                              StringRedisTemplate stringRedisTemplate,
                              SeckillOutboxService outboxService,
                              @Value("${app.seckill.rate-limit-per-second:2000}") int rateLimitPerSecond,
                              @Value("${app.seckill.reservation-ttl-seconds:604800}") long reservationTtlSeconds,
                              @Value("${app.seckill.stock-ttl-seconds:86400}") long stockTtlSeconds) {
        this.productMapper = productMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.outboxService = outboxService;
        this.rateLimitPerSecond = Math.max(1, rateLimitPerSecond);
        this.reservationTtlSeconds = Math.max(60, reservationTtlSeconds);
        this.stockTtlSeconds = Math.max(60, stockTtlSeconds);
    }

    @Override
    public void prepareStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }
        stringRedisTemplate.opsForValue().set(
                stockKey(productId),
                String.valueOf(product.getStock()),
                Duration.ofSeconds(stockTtlSeconds));
    }

    @Override
    public void seckill(Long userId, Long productId) {
        checkRateLimit(productId);

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(404, "商品不存在");
        }

        Long result = stringRedisTemplate.execute(
                reserveStockScript,
                List.of(stockKey(productId), userReservationKey(productId)),
                userId.toString(),
                "1",
                String.valueOf(reservationTtlSeconds));

        if (result == null || result == -1L) {
            throw new BusinessException(409, "库存尚未预热，请稍后再试");
        }
        if (result == 0L) {
            throw new BusinessException(409, "手慢了，商品已抢光");
        }
        if (result == 2L) {
            throw new BusinessException(409, "您已抢购过该商品，不能重复抢购");
        }
        if (result != 1L) {
            throw new BusinessException(500, "库存扣减失败");
        }

        String orderNo = OrderNoGenerator.next();
        try {
            outboxService.createPending(orderNo, userId, productId);
        } catch (DuplicateKeyException e) {
            compensateReservation(userId, productId);
            throw new BusinessException(409, "您已抢购过该商品，不能重复抢购");
        } catch (Exception e) {
            compensateReservation(userId, productId);
            throw new BusinessException(500, "抢购请求处理失败，请稍后再试");
        }
    }

    private void checkRateLimit(Long productId) {
        long second = Instant.now().getEpochSecond();
        String rateKey = "seckill:rate:" + productId + ":" + second;
        Long allowed = stringRedisTemplate.execute(
                rateLimitScript,
                List.of(rateKey),
                "2",
                String.valueOf(rateLimitPerSecond));
        if (allowed == null || allowed != 1L) {
            throw new BusinessException(429, "当前请求过多，请稍后再试");
        }
    }

    private void compensateReservation(Long userId, Long productId) {
        stringRedisTemplate.execute(
                compensateStockScript,
                List.of(stockKey(productId), userReservationKey(productId)),
                userId.toString(),
                "1");
    }

    private String stockKey(Long productId) {
        return "seckill:stock:" + productId;
    }

    private String userReservationKey(Long productId) {
        return "seckill:users:" + productId;
    }
}
