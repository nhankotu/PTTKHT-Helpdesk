package com.helpdesk.common.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class GeminiBoundedClientTest {
    HttpServer server;
    AtomicInteger calls = new AtomicInteger();
    @AfterEach void stop() { if (server != null) server.stop(0); }
    GeminiClient client(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/test:generateContent", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes(); byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        return new GeminiClient(new AiProperties("gemini", "test-key", "test", "http://127.0.0.1:" + server.getAddress().getPort(), 1000), new ObjectMapper());
    }
    String response(String data) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of("candidates", java.util.List.of(java.util.Map.of("finishReason", "STOP", "content", java.util.Map.of("parts", java.util.List.of(java.util.Map.of("text", data)))))));
    }
    @Test void parsesJsonOnceWithoutRepair() throws Exception {
        var client = client(200, response("{\"decision\":\"READY\"}"));
        assertThat(client.generateBoundedJson("system", "data", "test", 1000, 100).path("decision").asText()).isEqualTo("READY");
        assertThat(calls).hasValue(1);
    }
    @Test void rateLimitNeverRetriesAndOpensCircuitAfterThreeFailures() throws Exception {
        var client = client(429, "rate limited");
        for (int i=0; i<4; i++) assertThatThrownBy(() -> client.generateBoundedJson("system", "data", "test", 1000, 100)).isInstanceOf(RuntimeException.class);
        assertThat(calls).hasValue(3);
    }
    @Test void duplicateJsonKeysAndMarkdownAreRejectedWithoutRepair() throws Exception {
        var client = client(200, response("{\"decision\":\"READY\",\"decision\":\"ANSWER\"}"));
        assertThatThrownBy(() -> client.generateBoundedJson("system", "data", "test", 1000, 100)).isInstanceOf(RuntimeException.class);
        assertThat(calls).hasValue(1);
    }
    @Test void oversizedResponseIsRejected() throws Exception {
        var client = client(200, "x".repeat(140000));
        assertThatThrownBy(() -> client.generateBoundedJson("system", "data", "test", 1000, 100)).isInstanceOf(RuntimeException.class);
        assertThat(calls).hasValue(1);
    }
}
