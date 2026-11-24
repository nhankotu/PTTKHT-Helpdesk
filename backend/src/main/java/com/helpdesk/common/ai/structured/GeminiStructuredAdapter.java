package com.helpdesk.common.ai.structured;

import com.helpdesk.common.ai.GeminiClient;
import org.springframework.stereotype.Component;

@Component
public class GeminiStructuredAdapter implements StructuredModelAdapter {
    private final GeminiClient client;
    public GeminiStructuredAdapter(GeminiClient client) { this.client = client; }
    public String provider() { return "gemini"; }
    public Reply generate(Request request) { return client.generateBoundedReply(request.system(), request.inputJson(), request.model(), request.timeoutMs(), request.maxOutputTokens()); }
}
