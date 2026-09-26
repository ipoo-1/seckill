package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.seckill.common.BusinessException;
import com.seckill.entity.Order;
import com.seckill.entity.Product;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductMapper productMapper;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderMapper, productMapper);
    }

    @Test
    void createsOrderAndDecrementsMySqlStock() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectById(1L)).thenReturn(product());
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.createOrder(100L, 10L, 1L, "ORDER-1");

        verify(orderMapper).insert(any(Order.class));
        verify(productMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void duplicateMessageDoesNotCreateOrDeductAgain() {
        when(orderMapper.selectCount(any())).thenReturn(1L);

        service.createOrder(100L, 10L, 1L, "ORDER-1");

        verify(orderMapper, never()).insert(any(Order.class));
        verify(productMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void throwsWhenMySqlStockCannotBeDecremented() {
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectById(1L)).thenReturn(product());
        when(orderMapper.insert(any(Order.class))).thenReturn(1);
        when(productMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createOrder(100L, 10L, 1L, "ORDER-1"));

        assertEquals(409, error.getCode());
    }

    private Product product() {
        Product product = new Product();
        product.setId(1L);
        product.setName("test-product");
        product.setPrice(BigDecimal.ONE);
        product.setStock(10);
        return product;
    }
}
