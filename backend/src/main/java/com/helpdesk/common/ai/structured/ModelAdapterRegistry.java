package com.helpdesk.common.ai.structured;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ModelAdapterRegistry {
    private final Map<String, StructuredModelAdapter> adapters = new HashMap<>();
    public ModelAdapterRegistry(List<StructuredModelAdapter> registered) {
        for (var adapter : registered) if (adapters.putIfAbsent(adapter.provider(), adapter) != null) throw new IllegalArgumentException("Duplicate model provider");
    }
    public StructuredModelAdapter resolve(String provider) {
        var adapter = adapters.get(provider.toLowerCase(Locale.ROOT));
        if (adapter == null) throw new IllegalArgumentException("Unsupported structured model provider: " + provider);
        return adapter;
    }
}
