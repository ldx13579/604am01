package com.example.configcenter.integration;

import com.example.configcenter.config.TestRabbitMQConfig;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRabbitMQConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AdvancedLoadTest {

    @LocalServerPort
    private int port;

    private HttpClient httpClient;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    @BeforeEach
    void setup() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("场景1: 500客户端同时发起配置拉取 (高并发)")
    void scenario_500ConcurrentClients() throws Exception {
        PerformanceResult result = runLoadTest(500, 3, "高并发500客户端");
        assertTrue(result.successRate > 90.0,
                String.format("500并发成功率 %.2f%% < 90%%", result.successRate));
    }

    @Test
    @Order(2)
    @DisplayName("场景2: 瞬间流量脉冲 - 200客户端同时到达 x 10轮")
    void scenario_BurstTraffic() throws Exception {
        int bursts = 10;
        List<PerformanceResult> results = new ArrayList<>();

        for (int burst = 0; burst < bursts; burst++) {
            PerformanceResult result = runLoadTest(200, 1, "脉冲第" + (burst + 1) + "轮");
            results.add(result);
            Thread.sleep(500);
        }

        double avgSuccessRate = results.stream().mapToDouble(r -> r.successRate).average().orElse(0);
        double maxP99 = results.stream().mapToLong(r -> r.p99).max().orElse(0);

        System.out.println("\n=== 脉冲流量测试汇总 ===");
        System.out.printf("总脉冲轮次: %d%n", bursts);
        System.out.printf("平均成功率: %.2f%%%n", avgSuccessRate);
        System.out.printf("最大P99延迟: %d ms%n", maxP99);

        assertTrue(avgSuccessRate > 85.0);
    }

    @Test
    @Order(3)
    @DisplayName("场景3: 慢客户端混合 - 100正常 + 50慢速客户端")
    void scenario_SlowClients() throws Exception {
        int normalClients = 100;
        int slowClients = 50;
        int totalClients = normalClients + slowClients;

        ExecutorService executor = Executors.newFixedThreadPool(totalClients);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalClients);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);

        for (int i = 0; i < totalClients; i++) {
            final boolean isSlow = i >= normalClients;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (isSlow) Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));

                    long start = System.nanoTime();
                    HttpResponse<String> resp = sendPollingRequest();
                    long elapsed = (System.nanoTime() - start) / 1_000_000;
                    responseTimes.add(elapsed);

                    if (resp.statusCode() == 200) success.incrementAndGet();
                    else failure.incrementAndGet();
                } catch (Exception e) {
                    failure.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();

        double successRate = (double) success.get() / (success.get() + failure.get()) * 100;
        System.out.printf("\n=== 慢客户端混合测试 ===%n");
        System.out.printf("正常客户端: %d, 慢速客户端: %d%n", normalClients, slowClients);
        System.out.printf("成功率: %.2f%%%n", successRate);
        printPercentiles(responseTimes);

        assertTrue(successRate > 80.0);
    }

    @Test
    @Order(4)
    @DisplayName("场景4: 混合读写压力 - 80%读 + 20%写并发")
    void scenario_MixedReadWrite() throws Exception {
        int totalClients = 200;
        int writeClients = 40;
        int readClients = totalClients - writeClients;

        ExecutorService executor = Executors.newFixedThreadPool(totalClients);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalClients);
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger writeSuccess = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        // Read clients: poll for config
        for (int i = 0; i < readClients; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int round = 0; round < 3; round++) {
                        HttpResponse<String> resp = sendPollingRequest();
                        if (resp.statusCode() == 200) readSuccess.incrementAndGet();
                        else errors.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Write clients: create configs
        for (int i = 0; i < writeClients; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String body = String.format(
                            "{\"configKey\":\"load.write.%d.%d\",\"configValue\":\"value-%d\",\"environment\":\"dev\",\"namespace\":\"default\",\"description\":\"load test\"}",
                            idx, System.nanoTime(), idx);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/api/configs"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(10))
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 || resp.statusCode() == 201) writeSuccess.incrementAndGet();
                    else errors.incrementAndGet();
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();

        System.out.printf("\n=== 混合读写压力测试 ===%n");
        System.out.printf("读客户端: %d, 写客户端: %d%n", readClients, writeClients);
        System.out.printf("读成功: %d, 写成功: %d, 错误: %d%n",
                readSuccess.get(), writeSuccess.get(), errors.get());
        double overallSuccess = (double)(readSuccess.get() + writeSuccess.get()) /
                (readSuccess.get() + writeSuccess.get() + errors.get()) * 100;
        System.out.printf("综合成功率: %.2f%%%n", overallSuccess);

        assertTrue(overallSuccess > 75.0);
    }

    @Test
    @Order(5)
    @DisplayName("场景5: 长时间稳定性测试 - 50客户端持续30秒")
    void scenario_Stability() throws Exception {
        int clients = 50;
        int durationSeconds = 30;

        ExecutorService executor = Executors.newFixedThreadPool(clients);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFailure = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        List<Long> allLatencies = Collections.synchronizedList(new ArrayList<>());

        long memBefore = memoryBean.getHeapMemoryUsage().getUsed();
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);

        CountDownLatch doneLatch = new CountDownLatch(clients);
        for (int i = 0; i < clients; i++) {
            executor.submit(() -> {
                try {
                    while (System.currentTimeMillis() < endTime) {
                        long reqStart = System.nanoTime();
                        try {
                            HttpResponse<String> resp = sendPollingRequest();
                            long elapsed = (System.nanoTime() - reqStart) / 1_000_000;
                            allLatencies.add(elapsed);
                            totalLatency.addAndGet(elapsed);
                            totalRequests.incrementAndGet();
                            if (resp.statusCode() == 200) totalSuccess.incrementAndGet();
                            else totalFailure.incrementAndGet();
                        } catch (Exception e) {
                            totalFailure.incrementAndGet();
                            totalRequests.incrementAndGet();
                        }
                        Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        doneLatch.await(durationSeconds + 10, TimeUnit.SECONDS);
        executor.shutdownNow();

        long memAfter = memoryBean.getHeapMemoryUsage().getUsed();
        long actualDuration = System.currentTimeMillis() - startTime;

        double successRate = (double) totalSuccess.get() / totalRequests.get() * 100;
        double throughput = (double) totalRequests.get() / actualDuration * 1000;

        System.out.println("\n==========================================");
        System.out.println("   稳定性测试报告 (持续运行)");
        System.out.println("==========================================");
        System.out.printf("运行时长: %d 秒%n", actualDuration / 1000);
        System.out.printf("并发客户端: %d%n", clients);
        System.out.printf("总请求数: %d%n", totalRequests.get());
        System.out.printf("成功: %d, 失败: %d%n", totalSuccess.get(), totalFailure.get());
        System.out.printf("成功率: %.2f%%%n", successRate);
        System.out.printf("吞吐量: %.2f req/s%n", throughput);
        System.out.printf("平均延迟: %.2f ms%n",
                totalRequests.get() > 0 ? (double) totalLatency.get() / totalRequests.get() : 0);
        printPercentiles(allLatencies);
        System.out.printf("堆内存 (前): %.2f MB%n", memBefore / 1024.0 / 1024.0);
        System.out.printf("堆内存 (后): %.2f MB%n", memAfter / 1024.0 / 1024.0);
        System.out.printf("内存增长: %.2f MB%n", (memAfter - memBefore) / 1024.0 / 1024.0);
        System.out.println("==========================================\n");

        assertTrue(successRate > 95.0, "稳定性测试成功率应 > 95%");
        long memGrowthMB = (memAfter - memBefore) / 1024 / 1024;
        assertTrue(memGrowthMB < 200, "内存增长应 < 200MB (实际: " + memGrowthMB + "MB)");
    }

    @Test
    @Order(6)
    @DisplayName("场景6: 连接风暴 - 1000个短连接快速建立并释放")
    void scenario_ConnectionStorm() throws Exception {
        int connections = 1000;
        int batchSize = 100;
        int batches = connections / batchSize;

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);
        long totalStart = System.nanoTime();

        for (int batch = 0; batch < batches; batch++) {
            ExecutorService executor = Executors.newFixedThreadPool(batchSize);
            CountDownLatch latch = new CountDownLatch(batchSize);

            for (int i = 0; i < batchSize; i++) {
                executor.submit(() -> {
                    try {
                        HttpClient client = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/version?env=dev&ns=default"))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) success.incrementAndGet();
                        else failure.incrementAndGet();
                    } catch (Exception e) {
                        failure.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdownNow();
        }

        long totalDuration = (System.nanoTime() - totalStart) / 1_000_000;
        double successRate = (double) success.get() / (success.get() + failure.get()) * 100;

        System.out.printf("\n=== 连接风暴测试 ===%n");
        System.out.printf("总连接数: %d (分 %d 批, 每批 %d)%n", connections, batches, batchSize);
        System.out.printf("成功: %d, 失败: %d%n", success.get(), failure.get());
        System.out.printf("成功率: %.2f%%%n", successRate);
        System.out.printf("总耗时: %d ms%n", totalDuration);
        System.out.printf("连接建立速率: %.2f conn/s%n", (double) connections / totalDuration * 1000);

        assertTrue(successRate > 80.0, "连接风暴成功率应 > 80%");
    }

    @Test
    @Order(7)
    @DisplayName("场景7: 超大响应体 - 大量配置项同时返回")
    void scenario_LargeResponse() throws Exception {
        // Create 50 configs with large values
        for (int i = 0; i < 50; i++) {
            String body = String.format(
                    "{\"configKey\":\"large.payload.%d\",\"configValue\":\"%s\",\"environment\":\"dev\",\"namespace\":\"default\",\"description\":\"load test large payload\"}",
                    i, "X".repeat(1024));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/configs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            try {
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        }

        // Now test polling with large response
        PerformanceResult result = runLoadTest(100, 3, "大响应体");
        System.out.printf("\n=== 大响应体测试 ===%n");
        System.out.printf("配置项数: 50 (每项 1KB)%n");
        System.out.printf("响应体估算: ~50KB%n");
        System.out.printf("成功率: %.2f%%%n", result.successRate);
        System.out.printf("P99延迟: %d ms%n", result.p99);

        assertTrue(result.successRate > 85.0);
    }

    // ==================== Helper Methods ====================

    private PerformanceResult runLoadTest(int clientCount, int rounds, String label) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(clientCount, 200));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();
        long testStart = System.nanoTime();

        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int round = 0; round < rounds; round++) {
                        long start = System.nanoTime();
                        try {
                            HttpResponse<String> resp = sendPollingRequest();
                            long elapsed = (System.nanoTime() - start) / 1_000_000;
                            responseTimes.add(elapsed);
                            if (resp.statusCode() == 200) success.incrementAndGet();
                            else failure.incrementAndGet();
                        } catch (Exception e) {
                            responseTimes.add((System.nanoTime() - start) / 1_000_000);
                            failure.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean done = doneLatch.await(120, TimeUnit.SECONDS);
        long testDuration = (System.nanoTime() - testStart) / 1_000_000;
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        executor.shutdownNow();

        assertTrue(done, label + " 超时");

        int total = success.get() + failure.get();
        double successRate = total > 0 ? (double) success.get() / total * 100 : 0;
        long[] sorted = responseTimes.stream().mapToLong(Long::longValue).sorted().toArray();

        long p50 = sorted.length > 0 ? sorted[(int)(sorted.length * 0.50)] : 0;
        long p95 = sorted.length > 0 ? sorted[(int)(sorted.length * 0.95)] : 0;
        long p99 = sorted.length > 0 ? sorted[(int)(sorted.length * 0.99)] : 0;
        long max = sorted.length > 0 ? sorted[sorted.length - 1] : 0;
        double avg = Arrays.stream(sorted).average().orElse(0);
        double throughput = total > 0 ? (double) total / testDuration * 1000 : 0;

        System.out.printf("\n--- [%s] 并发=%d, 轮次=%d ---%n", label, clientCount, rounds);
        System.out.printf("总请求: %d, 成功: %d, 失败: %d, 成功率: %.2f%%%n",
                total, success.get(), failure.get(), successRate);
        System.out.printf("延迟: avg=%.1fms, p50=%dms, p95=%dms, p99=%dms, max=%dms%n",
                avg, p50, p95, p99, max);
        System.out.printf("吞吐量: %.2f req/s, 耗时: %dms%n", throughput, testDuration);
        System.out.printf("内存: before=%.1fMB, after=%.1fMB, delta=%.1fMB%n",
                memBefore/1024.0/1024.0, memAfter/1024.0/1024.0,
                (memAfter-memBefore)/1024.0/1024.0);

        return new PerformanceResult(successRate, p50, p95, p99, max, avg, throughput, testDuration);
    }

    private HttpResponse<String> sendPollingRequest() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/polling?env=dev&ns=default&clientVersion=0"))
                .timeout(Duration.ofSeconds(35))
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private void printPercentiles(List<Long> times) {
        if (times.isEmpty()) return;
        long[] sorted = times.stream().mapToLong(Long::longValue).sorted().toArray();
        System.out.printf("延迟分布: p50=%dms, p95=%dms, p99=%dms, max=%dms%n",
                sorted[(int)(sorted.length * 0.50)],
                sorted[(int)(sorted.length * 0.95)],
                sorted[Math.min((int)(sorted.length * 0.99), sorted.length - 1)],
                sorted[sorted.length - 1]);
    }

    record PerformanceResult(double successRate, long p50, long p95, long p99,
                             long max, double avg, double throughput, long duration) {}
}
