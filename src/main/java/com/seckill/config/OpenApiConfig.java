package com.seckill.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Seckill API",
                version = "1.0.0",
                description = "秒杀系统：Redis 原子扣库存、可靠 Outbox、RocketMQ 异步建单。"))
public class OpenApiConfig {
}
