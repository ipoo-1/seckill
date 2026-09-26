package com.seckill;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires MySQL, Redis, RocketMQ and Elasticsearch; run only in an integration environment.")
@SpringBootTest
class SeckillApplicationTests {

	@Test
	void contextLoads() {
	}

}
