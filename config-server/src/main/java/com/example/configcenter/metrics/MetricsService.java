package com.example.configcenter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MetricsService {

    private final MeterRegistry registry;
    private final Timer pollingLatencyTimer;
    private final Counter pollingFailureCounter;
    private final AtomicInteger activePollingClients;
    private final AtomicInteger zombieConfigCount;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.pollingLatencyTimer = Timer.builder("config.polling.latency")
                .description("Long-polling response latency")
                .register(registry);
        this.pollingFailureCounter = Counter.builder("config.polling.failures")
                .description("Polling failure count")
                .register(registry);
        this.activePollingClients = new AtomicInteger(0);
        registry.gauge("config.polling.active_clients", activePollingClients);
        this.zombieConfigCount = new AtomicInteger(0);
        registry.gauge("config.zombie.count", zombieConfigCount);
    }

    public void recordConfigChange(String environment, String operation) {
        Counter.builder("config.change.total")
                .tag("env", environment)
                .tag("operation", operation)
                .register(registry)
                .increment();
    }

    public Timer.Sample startPollingTimer() {
        return Timer.start(registry);
    }

    public void stopPollingTimer(Timer.Sample sample) {
        sample.stop(pollingLatencyTimer);
    }

    public void recordPollingFailure() {
        pollingFailureCounter.increment();
    }

    public void incrementActiveClients() {
        activePollingClients.incrementAndGet();
    }

    public void decrementActiveClients() {
        activePollingClients.decrementAndGet();
    }

    public void setZombieCount(int count) {
        zombieConfigCount.set(count);
    }

    public void recordChangeTestResult(String result) {
        Counter.builder("config.change_test.total")
                .tag("result", result)
                .register(registry)
                .increment();
    }
}
