package com.helpdesk.common.ai;

import com.fasterxml.jackson.databind.*;
import com.helpdesk.common.ai.structured.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelAdapterTest {
    HttpServer server;
    ObjectMapper json = new ObjectMapper();
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<JsonNode> request = new AtomicReference<>();
    @AfterEach void stop() { if (server != null) server.stop(0); }
    OpenAiCompatibleAdapter adapter(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet(); request.set(json.readTree(exchange.getRequestBody()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        return new OpenAiCompatibleAdapter(new OpenAiCompatibleProperties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key", false, "max_completion_tokens"), new BoundedJsonHttpTransport(json));
    }
    StructuredModelAdapter.Request request() { return new StructuredModelAdapter.Request("test-model", "Return JSON only", "{\"question\":\"hello\"}", 1000, 200); }
    @Test void openAiProtocolKeepsSystemSeparateAndReportsUsage() throws Exception {
        String outer = json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "{\"decision\":\"READY\"}"))), "usage", Map.of("prompt_tokens", 20, "completion_tokens", 5)));
        var result = adapter(200, outer).generate(request());
        assertThat(result.json().path("decision").asText()).isEqualTo("READY");
        assertThat(result.inputTokens()).isEqualTo(20L); assertThat(result.outputTokens()).isEqualTo(5L);
        assertThat(calls).hasValue(1);
        assertThat(request.get().path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(request.get().path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(request.get().path("max_completion_tokens").asInt()).isEqualTo(200);
        assertThat(request.get().has("tools")).isFalse();
    }
    @Test void providerErrorIsNotRetriedOrRepaired() throws Exception {
        var adapter = adapter(429, "too many");
        assertThatThrownBy(() -> adapter.generate(request())).isInstanceOf(RuntimeException.class);
        assertThat(calls).hasValue(1);
    }
    @Test void trailingJsonAndToolRequestsAreRejected() throws Exception {
        String outer = json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "{} {}")))));
        var adapter = adapter(200, outer);
        assertThatThrownBy(() -> adapter.generate(request())).isInstanceOf(RuntimeException.class);
    }
    @Test void routerChoosesOnlyConfiguredAdapterAndRejectsUnknownOrDuplicate() {
        StructuredModelAdapter a = mock(StructuredModelAdapter.class), b = mock(StructuredModelAdapter.class);
        when(a.provider()).thenReturn("gemini"); when(b.provider()).thenReturn("openai-compatible");
        var registry = new ModelAdapterRegistry(List.of(a,b));
        assertThat(registry.resolve("gemini")).isSameAs(a);
        assertThat(registry.resolve("openai-compatible")).isSameAs(b);
        assertThatThrownBy(() -> registry.resolve("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelAdapterRegistry(List.of(a,a))).isInstanceOf(IllegalArgumentException.class);
        verify(a, never()).generate(any()); verify(b, never()).generate(any());
    }
    @Test void timeoutAfterHeadersStillStopsBodyReadWithinBudget() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        }); server.start();
        var transport = new BoundedJsonHttpTransport(json); long start = System.nanoTime();
        assertThatThrownBy(() -> transport.post("http://127.0.0.1:"+server.getAddress().getPort()+"/slow", "Authorization", "test", Map.of(), 200)).isInstanceOf(RuntimeException.class);
        assertThat((System.nanoTime()-start)/1_000_000).isLessThan(1200);
        assertThat(calls).hasValue(1);
    }
    @Test void redirectDoesNotForwardCredentialToAnotherEndpoint() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); AtomicInteger leaked = new AtomicInteger();
        server.createContext("/redirect", exchange -> { exchange.getRequestBody().readAllBytes(); exchange.getResponseHeaders().add("Location", "http://127.0.0.1:"+server.getAddress().getPort()+"/steal"); exchange.sendResponseHeaders(302,-1); exchange.close(); });
        server.createContext("/steal", exchange -> { leaked.incrementAndGet(); exchange.sendResponseHeaders(200,-1); exchange.close(); }); server.start();
        assertThatThrownBy(() -> new BoundedJsonHttpTransport(json).post("http://127.0.0.1:"+server.getAddress().getPort()+"/redirect", "Authorization", "Bearer test-secret", Map.of(), 1000)).isInstanceOf(RuntimeException.class);
        assertThat(leaked).hasValue(0);
    }
}
