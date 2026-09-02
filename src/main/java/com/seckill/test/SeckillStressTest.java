package com.seckill.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SeckillStressTest {

    public static void main(String[] args) throws Exception {
        int threads = 50; // 模拟 50 个用户同时抢
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads); // 所有线程就绪
        CountDownLatch start = new CountDownLatch(1);       // 发令枪
        CountDownLatch done = new CountDownLatch(threads);  // 全部完成
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < threads; i++) {
            final int userId = 1000 + i; // 每个用户不同
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await(); // 所有人到齐才开抢
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/seckill/1?userId=" + userId))
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String body = response.body();
                    if (body.contains("\"code\":200")) {
                        success.incrementAndGet();
                        System.out.println("用户" + userId + " 抢购成功！");
                    } else {
                        fail.incrementAndGet();
                        System.out.println("用户" + userId + " 失败: " + body);
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                    System.out.println("用户" + userId + " 异常: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();               // 等 50 个线程就绪
        long begin = System.currentTimeMillis();
        start.countDown();           // 发令枪响！
        done.await();                // 等全部跑完
        long cost = System.currentTimeMillis() - begin;
        pool.shutdown();

        System.out.println("==============================");
        System.out.println("并发数: " + threads);
        System.out.println("耗时: " + cost + "ms");
        System.out.println("抢购成功: " + success.get());
        System.out.println("抢购失败: " + fail.get());
    }
}