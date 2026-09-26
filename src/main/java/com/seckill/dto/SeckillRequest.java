package com.seckill.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SeckillRequest(
        @NotNull(message = "userId 不能为空")
        @Positive(message = "userId 必须大于 0")
        Long userId
) {
}
