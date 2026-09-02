package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.common.BusinessException;
import com.seckill.entity.Order;
import com.seckill.entity.Product;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    @Transactional
    public void createOrder(Long userId, Long productId) {
        // 1. 查商品
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        // 2. 乐观锁扣减 MySQL 库存（防超卖的"第二道保险"）
        //    相当于 SQL：UPDATE product SET stock = stock - 1 WHERE id = ? AND stock > 0
        int rows = productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .setSql("stock = stock - 1")
                .eq(Product::getId, productId)
                .gt(Product::getStock, 0));
        if (rows == 0) {
            throw new BusinessException("库存不足");
        }

        // 3. 生成订单号并插入订单（状态 0 = 待支付）
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setPrice(product.getPrice());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
    }
}