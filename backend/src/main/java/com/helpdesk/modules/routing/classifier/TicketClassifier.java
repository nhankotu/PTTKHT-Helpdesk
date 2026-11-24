package com.helpdesk.modules.routing.classifier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface phân loại ticket bằng AI (UC04).
 * Cho phép thay thế linh hoạt giữa OpenAiTicketClassifier (LLM API) và Heuristic/Mock cho offline/test.
 */
public interface TicketClassifier {

    record ClassificationSuggestion(
            String categoryCode,
            BigDecimal confidence,
            String modelVersion,
            String reason
    ) {
    }

    ClassificationSuggestion classify(String subject, String publicDescription, List<String> allowedCategoryCodes);
}
