package com.helpdesk.common.ai.structured;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded full-body read, including servers that send headers then stall; never follows redirects. */
@Component
public class BoundedJsonHttpTransport {
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, Circuit> circuits = new ConcurrentHashMap<>();
    private static class Circuit { final AtomicInteger failures = new AtomicInteger(); final AtomicLong openUntil = new AtomicLong(); }
    public BoundedJsonHttpTransport(ObjectMapper mapper) { this.mapper = mapper; }
    public JsonNode parse(String raw) {
        if (raw == null || raw.length() > 32000) throw new IllegalStateException("Invalid model JSON size");
        try {
            JsonNode result = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
            if (result == null || !result.isObject()) throw new IllegalStateException("Invalid model JSON");
            return result;
        } catch (Exception ex) { throw new IllegalStateException("Invalid model JSON"); }
    }
    public JsonNode post(String endpoint, String header, String credential, Object payload, int timeoutMs) {
        URI uri = URI.create(endpoint);
        boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost());
        if (!("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local)) || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Model endpoint requires HTTPS or loopback HTTP, without URL credentials");
        Circuit circuit = circuits.computeIfAbsent(endpoint, key -> new Circuit());
        if (System.nanoTime() < circuit.openUntil.get()) throw new IllegalStateException("Model circuit open");
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            byte[] bytes = mapper.writeValueAsBytes(payload);
            if (bytes.length > 262144 || timeoutMs < 100 || timeoutMs > 25000) throw new IllegalArgumentException("Model request limit exceeded");
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofMillis(Math.min(3000, timeoutMs))).build();
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
            if (credential != null && !credential.isBlank()) builder.header(header, credential);
            pending = client.sendAsync(builder.build(), info -> new LimitedBody());
            var response = pending.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Model HTTP failure");
            JsonNode result = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.body());
            if (result == null || !result.isObject()) throw new IllegalStateException("Model envelope invalid");
            circuit.failures.set(0); return result;
        } catch (Exception ex) {
            if (pending != null) pending.cancel(true);
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            if (circuit.failures.incrementAndGet() >= 3) circuit.openUntil.set(System.nanoTime() + 30_000_000_000L);
            throw new IllegalStateException("Bounded model HTTP request failed");
        }
    }
    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> body = new CompletableFuture<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer buffer : items) {
                if (bytes.size() + buffer.remaining() > 131072) {
                    subscription.cancel(); body.completeExceptionally(new IllegalStateException("Model response limit exceeded")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
