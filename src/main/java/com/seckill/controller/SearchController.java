package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.Product;
import com.seckill.service.ProductSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private ProductSearchService productSearchService;

    // 同步商品到 ES
    @GetMapping("/sync")
    public Result<Void> sync() {
        productSearchService.syncAll();
        return Result.success();
    }

    // 搜索商品
    @GetMapping
    public Result<List<Product>> search(@RequestParam String keyword) {
        return Result.success(productSearchService.search(keyword));
    }
}