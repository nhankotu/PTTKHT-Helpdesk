package com.helpdesk.common.ai.structured;

import com.fasterxml.jackson.databind.JsonNode;

/** Provider boundary: no retrieval, authorization, tools or automatic retries belong here. */
public interface StructuredModelAdapter {
    String provider();
    Reply generate(Request request);
    record Request(String model, String system, String inputJson, int timeoutMs, int maxOutputTokens) {
        public Request {
            if (model == null || !model.matches("[A-Za-z0-9._:/-]{1,80}") || system == null || inputJson == null
                    || timeoutMs < 100 || timeoutMs > 25000 || maxOutputTokens < 16 || maxOutputTokens > 8000)
                throw new IllegalArgumentException("Invalid structured model request");
        }
    }
    record Reply(JsonNode json, Long inputTokens, Long outputTokens) {}
}
