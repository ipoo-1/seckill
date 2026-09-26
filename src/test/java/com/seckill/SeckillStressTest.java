package com.seckill;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手工压测工具，不参与 mvn test 自动执行。
 */
public final class SeckillStressTest {

    private SeckillStressTest() {
    }

    public static void main(String[] args) throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < threads; i++) {
            final long userId = 1000L + i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    String json = "{\"userId\":" + userId + "}";
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/seckill/1"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = client.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.body().contains("\"code\":200")) {
                        success.incrementAndGet();
                    } else {
                        fail.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long begin = System.currentTimeMillis();
        start.countDown();
        done.await();
        long cost = System.currentTimeMillis() - begin;
        pool.shutdown();

        System.out.println("concurrency=" + threads);
        System.out.println("costMs=" + cost);
        System.out.println("success=" + success.get());
        System.out.println("fail=" + fail.get());
    }
}
