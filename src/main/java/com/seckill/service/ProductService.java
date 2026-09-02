package com.seckill.service;

import com.seckill.entity.Product;

import java.util.List;

public interface ProductService {
    List<Product> listProducts();
    Product getProductById(Long id);
}