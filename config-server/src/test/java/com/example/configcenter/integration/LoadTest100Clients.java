package com.example.configcenter.integration;

import com.example.configcenter.config.TestRabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRabbitMQConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
public class LoadTest100Clients {

    @LocalServerPort
    private int port;

    private static final int CLIENT_COUNT = 100;
    private static final int POLLING_ROUNDS = 5;

    @Test
    void testHundredConcurrentClientsPolling() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CLIENT_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(CLIENT_COUNT);

        List<Long> allResponseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuBefore = osBean.getSystemLoadAverage();

        long testStartTime = System.nanoTime();

        for (int i = 0; i < CLIENT_COUNT; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int round = 0; round < POLLING_ROUNDS; round++) {
                        long start = System.nanoTime();
                        try {
                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(String.format(
                                            "http://localhost:%d/api/polling?env=dev&ns=default&clientVersion=0",
                                            port)))
                                    .timeout(Duration.ofSeconds(35))
                                    .GET()
                                    .build();

                            HttpResponse<String> response = httpClient.send(request,
                                    HttpResponse.BodyHandlers.ofString());

                            long elapsed = (System.nanoTime() - start) / 1_000_000;
                            allResponseTimes.add(elapsed);

                            if (response.statusCode() == 200) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            long elapsed = (System.nanoTime() - start) / 1_000_000;
                            allResponseTimes.add(elapsed);
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completeLatch.await(180, TimeUnit.SECONDS);
        long testDuration = (System.nanoTime() - testStartTime) / 1_000_000;

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        double cpuAfter = osBean.getSystemLoadAverage();

        executor.shutdownNow();

        assertTrue(completed, "Load test did not complete within timeout");

        int totalRequests = successCount.get() + failureCount.get();
        double successRate = (double) successCount.get() / totalRequests * 100;

        long[] times = allResponseTimes.stream().mapToLong(Long::longValue).sorted().toArray();
        long min = times[0];
        long max = times[times.length - 1];
        double avg = Arrays.stream(times).average().orElse(0);
        long p50 = times[(int) (times.length * 0.50)];
        long p95 = times[(int) (times.length * 0.95)];
        long p99 = times[(int) (times.length * 0.99)];
        double throughput = (double) totalRequests / testDuration * 1000;

        System.out.println("\n========================================");
        System.out.println("   配置中心压测报告 - 100客户端并发拉取");
        System.out.println("========================================");
        System.out.println();
        System.out.printf("并发客户端数: %d%n", CLIENT_COUNT);
        System.out.printf("每客户端轮询次数: %d%n", POLLING_ROUNDS);
        System.out.printf("总请求数: %d%n", totalRequests);
        System.out.printf("成功请求: %d%n", successCount.get());
        System.out.printf("失败请求: %d%n", failureCount.get());
        System.out.printf("成功率: %.2f%%%n", successRate);
        System.out.println();
        System.out.println("--- 响应延迟 ---");
        System.out.printf("最小: %d ms%n", min);
        System.out.printf("平均: %.2f ms%n", avg);
        System.out.printf("P50: %d ms%n", p50);
        System.out.printf("P95: %d ms%n", p95);
        System.out.printf("P99: %d ms%n", p99);
        System.out.printf("最大: %d ms%n", max);
        System.out.println();
        System.out.println("--- 吞吐量 ---");
        System.out.printf("测试总时长: %d ms%n", testDuration);
        System.out.printf("吞吐量: %.2f req/s%n", throughput);
        System.out.println();
        System.out.println("--- 服务端资源消耗 ---");
        System.out.printf("内存 (测试前): %.2f MB%n", memBefore / 1024.0 / 1024.0);
        System.out.printf("内存 (测试后): %.2f MB%n", memAfter / 1024.0 / 1024.0);
        System.out.printf("内存增量: %.2f MB%n", (memAfter - memBefore) / 1024.0 / 1024.0);
        System.out.printf("系统负载 (测试前): %.2f%n", cpuBefore);
        System.out.printf("系统负载 (测试后): %.2f%n", cpuAfter);
        System.out.println();
        System.out.println("========================================");
        System.out.println();

        assertTrue(successRate > 95.0,
                String.format("Success rate %.2f%% is below 95%% threshold", successRate));
    }
}
