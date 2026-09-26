package com.seckill.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderNoGenerator() {
    }

    public static String next() {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return LocalDateTime.now().format(FORMATTER) + random;
    }
}
