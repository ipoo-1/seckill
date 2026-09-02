package com.seckill.service;

import com.seckill.entity.Product;

import java.util.List;

public interface ProductSearchService {
    // 把 MySQL 所有商品同步到 ES
    void syncAll();

    // 用关键词搜索商品（匹配名字+描述）
    List<Product> search(String keyword);
}