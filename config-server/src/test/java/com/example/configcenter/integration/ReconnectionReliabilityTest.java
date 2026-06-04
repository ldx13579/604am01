package com.example.configcenter.integration;

import com.example.configcenter.config.TestRabbitMQConfig;
import com.example.configcenter.model.entity.ClientReconnectLog;
import com.example.configcenter.repository.ClientReconnectLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRabbitMQConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration"
})
public class ReconnectionReliabilityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ClientReconnectLogRepository reconnectLogRepository;

    private static final int CLIENT_COUNT = 10;
    private static final String ENVIRONMENT = "dev";
    private static final String NAMESPACE = "default";

    @BeforeEach
    void setUp() {
        reconnectLogRepository.deleteAll();
    }

    @Test
    void testTenClientsReconnection() throws Exception {
        AtomicBoolean serverAvailable = new AtomicBoolean(true);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(CLIENT_COUNT);
        CountDownLatch initialConnectLatch = new CountDownLatch(CLIENT_COUNT);
        CountDownLatch reconnectLatch = new CountDownLatch(CLIENT_COUNT);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        for (int i = 0; i < CLIENT_COUNT; i++) {
            final String clientId = "client-" + i;
            executor.submit(() -> {
                try {
                    simulateClientLifecycle(
                            httpClient, clientId, serverAvailable,
                            initialConnectLatch, reconnectLatch);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // Wait for all clients to establish initial connection
        boolean allConnected = initialConnectLatch.await(30, TimeUnit.SECONDS);
        assertTrue(allConnected, "All clients should connect within 30 seconds");

        // Simulate server disruption: toggle flag so clients experience disconnect
        serverAvailable.set(false);

        // Wait a moment, then restore availability
        Thread.sleep(2000);
        serverAvailable.set(true);

        // Wait for all clients to reconnect
        boolean allReconnected = reconnectLatch.await(60, TimeUnit.SECONDS);
        assertTrue(allReconnected, "All clients should reconnect within 60 seconds");

        // Shut down executor
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Allow a brief moment for DB writes to flush
        Thread.sleep(500);

        // Verify reconnect logs
        long disconnectCount = reconnectLogRepository.countByEventType("DISCONNECT");
        long reconnectSuccessCount = reconnectLogRepository.countByEventType("RECONNECT_SUCCESS");
        long reconnectAttemptCount = reconnectLogRepository.countByEventType("RECONNECT_ATTEMPT");

        assertEquals(CLIENT_COUNT, disconnectCount,
                "Should have " + CLIENT_COUNT + " DISCONNECT events");
        assertEquals(CLIENT_COUNT, reconnectSuccessCount,
                "Should have " + CLIENT_COUNT + " RECONNECT_SUCCESS events");
        assertTrue(reconnectAttemptCount >= CLIENT_COUNT,
                "Should have at least " + CLIENT_COUNT + " RECONNECT_ATTEMPT events");

        // Verify each client has proper event sequence
        for (int i = 0; i < CLIENT_COUNT; i++) {
            String clientId = "client-" + i;
            List<ClientReconnectLog> clientLogs = reconnectLogRepository.findByClientId(clientId);
            assertTrue(clientLogs.size() >= 3,
                    "Client " + clientId + " should have at least 3 log entries (DISCONNECT, ATTEMPT, SUCCESS)");
        }
    }

    private void simulateClientLifecycle(
            HttpClient httpClient,
            String clientId,
            AtomicBoolean serverAvailable,
            CountDownLatch initialConnectLatch,
            CountDownLatch reconnectLatch) throws Exception {

        String pollingUrl = "http://localhost:" + port
                + "/api/polling?env=" + ENVIRONMENT + "&ns=" + NAMESPACE + "&clientVersion=0";

        // Phase 1: Initial connection
        long startTime = System.currentTimeMillis();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pollingUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long duration = System.currentTimeMillis() - startTime;

        if (response.statusCode() == 200) {
            initialConnectLatch.countDown();
        }

        // Wait for the server disruption signal
        while (serverAvailable.get()) {
            Thread.sleep(100);
        }

        // Phase 2: Record disconnect event
        ClientReconnectLog disconnectLog = new ClientReconnectLog();
        disconnectLog.setClientId(clientId);
        disconnectLog.setEnvironment(ENVIRONMENT);
        disconnectLog.setNamespace(NAMESPACE);
        disconnectLog.setEventType("DISCONNECT");
        disconnectLog.setAttemptNumber(0);
        disconnectLog.setVersionBefore(0L);
        disconnectLog.setDurationMs(duration);
        reconnectLogRepository.save(disconnectLog);

        // Simulate network interruption (client is unable to reach server)
        Thread.sleep(5000);

        // Phase 3: Reconnection attempts
        int attemptNumber = 0;
        boolean reconnected = false;

        while (!reconnected && attemptNumber < 5) {
            attemptNumber++;
            long attemptStart = System.currentTimeMillis();

            // Log reconnect attempt
            ClientReconnectLog attemptLog = new ClientReconnectLog();
            attemptLog.setClientId(clientId);
            attemptLog.setEnvironment(ENVIRONMENT);
            attemptLog.setNamespace(NAMESPACE);
            attemptLog.setEventType("RECONNECT_ATTEMPT");
            attemptLog.setAttemptNumber(attemptNumber);
            attemptLog.setVersionBefore(0L);
            reconnectLogRepository.save(attemptLog);

            if (serverAvailable.get()) {
                try {
                    HttpRequest reconnectRequest = HttpRequest.newBuilder()
                            .uri(URI.create(pollingUrl))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();

                    HttpResponse<String> reconnectResponse = httpClient.send(
                            reconnectRequest, HttpResponse.BodyHandlers.ofString());
                    long attemptDuration = System.currentTimeMillis() - attemptStart;

                    if (reconnectResponse.statusCode() == 200) {
                        // Log successful reconnection
                        ClientReconnectLog successLog = new ClientReconnectLog();
                        successLog.setClientId(clientId);
                        successLog.setEnvironment(ENVIRONMENT);
                        successLog.setNamespace(NAMESPACE);
                        successLog.setEventType("RECONNECT_SUCCESS");
                        successLog.setAttemptNumber(attemptNumber);
                        successLog.setVersionBefore(0L);
                        successLog.setVersionAfter(0L);
                        successLog.setDurationMs(attemptDuration);
                        reconnectLogRepository.save(successLog);

                        reconnected = true;
                        reconnectLatch.countDown();
                    }
                } catch (Exception e) {
                    // Log failed reconnection attempt
                    ClientReconnectLog failLog = new ClientReconnectLog();
                    failLog.setClientId(clientId);
                    failLog.setEnvironment(ENVIRONMENT);
                    failLog.setNamespace(NAMESPACE);
                    failLog.setEventType("RECONNECT_FAILED");
                    failLog.setAttemptNumber(attemptNumber);
                    failLog.setErrorMessage(e.getMessage());
                    failLog.setDurationMs(System.currentTimeMillis() - attemptStart);
                    reconnectLogRepository.save(failLog);

                    // Exponential backoff
                    Thread.sleep(1000L * attemptNumber);
                }
            } else {
                // Server not available yet, wait and retry
                Thread.sleep(1000L * attemptNumber);
            }
        }
    }
}
