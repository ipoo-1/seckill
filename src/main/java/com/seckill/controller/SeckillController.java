package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    // 预热库存：把 MySQL 库存同步到 Redis
    @GetMapping("/prepare/{productId}")
    public Result<Void> prepare(@PathVariable Long productId) {
        seckillService.prepareStock(productId);
        return Result.success();
    }

    // 抢购
    @GetMapping("/{productId}")
    public Result<Void> seckill(@PathVariable Long productId,
                                @RequestParam Long userId) {
        seckillService.seckill(userId, productId);
        return Result.success();
    }
}