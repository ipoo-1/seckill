package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.dto.SeckillRequest;
import com.seckill.service.SeckillService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    // 预热库存：把 MySQL 库存同步到 Redis
    @PostMapping("/prepare/{productId}")
    public Result<Void> prepare(@PathVariable Long productId) {
        seckillService.prepareStock(productId);
        return Result.success();
    }

    // 抢购
    @PostMapping("/{productId}")
    public Result<Void> seckill(@PathVariable Long productId,
                                @Valid @RequestBody SeckillRequest request) {
        seckillService.seckill(request.userId(), productId);
        return Result.success();
    }
}
