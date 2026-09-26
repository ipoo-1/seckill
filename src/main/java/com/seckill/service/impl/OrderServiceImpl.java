package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.common.BusinessException;
import com.seckill.entity.Order;
import com.seckill.entity.Product;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.OrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;

    public OrderServiceImpl(OrderMapper orderMapper, ProductMapper productMapper) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public void createOrder(Long outboxId, Long userId, Long productId, String orderNo) {
        if (existsOrder(userId, productId)) {
            return;
        }

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        // 先插入订单，再扣 MySQL 库存。订单表的 uk_orders_user_product
        // 是全局幂等边界，重复消息不会重复扣 MySQL 库存。
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setPrice(product.getPrice());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            if (existsOrder(userId, productId)) {
                return;
            }
            throw e;
        }

        // MySQL 条件更新作为库存兜底，Redis 与 MySQL 最终收敛。
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .setSql("stock = stock - 1")
                .eq(Product::getId, productId)
                .gt(Product::getStock, 0));
        if (rows == 0) {
            throw new BusinessException(409, "库存不一致，订单创建失败，等待重试");
        }
    }

    private boolean existsOrder(Long userId, Long productId) {
        return orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getProductId, productId)) > 0;
    }
}
