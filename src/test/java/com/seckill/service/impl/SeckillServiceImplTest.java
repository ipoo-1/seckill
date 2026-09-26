package com.seckill.service.impl;

import com.seckill.common.BusinessException;
import com.seckill.entity.Product;
import com.seckill.mapper.ProductMapper;
import com.seckill.service.SeckillOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SeckillOutboxService outboxService;

    private SeckillServiceImpl service;
    private final ArrayDeque<Long> executeResults = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class, invocation -> {
            if ("execute".equals(invocation.getMethod().getName())) {
                return invocation.getArguments().length == 4
                        ? 1L
                        : executeResults.remove();
            }
            return null;
        });
        service = new SeckillServiceImpl(productMapper, stringRedisTemplate, outboxService, 1000, 3600, 3600);
    }

    @Test
    void createsPendingOutboxAfterReservationSucceeds() {
        when(productMapper.selectById(1L)).thenReturn(product());
        stubExecute(1L);

        service.seckill(10L, 1L);

        verify(outboxService).createPending(anyString(), eq(10L), eq(1L));
    }

    @Test
    void rejectsWhenStockIsSoldOut() {
        when(productMapper.selectById(1L)).thenReturn(product());
        stubExecute(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.seckill(10L, 1L));

        assertEquals(409, error.getCode());
        verify(outboxService, never()).createPending(anyString(), any(), any());
    }

    @Test
    void rejectsDuplicateReservation() {
        when(productMapper.selectById(1L)).thenReturn(product());
        stubExecute(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.seckill(10L, 1L));

        assertEquals(409, error.getCode());
        verify(outboxService, never()).createPending(anyString(), any(), any());
    }

    @Test
    void compensatesRedisReservationWhenOutboxWriteFails() {
        when(productMapper.selectById(1L)).thenReturn(product());
        stubExecute(1L, 1L);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(outboxService).createPending(anyString(), eq(10L), eq(1L));

        assertThrows(BusinessException.class, () -> service.seckill(10L, 1L));

        verify(stringRedisTemplate, org.mockito.Mockito.times(2))
                .execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString());
    }

    private void stubExecute(Long... results) {
        executeResults.clear();
        executeResults.addAll(Arrays.asList(results));
    }

    private Product product() {
        Product product = new Product();
        product.setId(1L);
        product.setName("test-product");
        product.setPrice(BigDecimal.ONE);
        product.setStock(1);
        return product;
    }
}
