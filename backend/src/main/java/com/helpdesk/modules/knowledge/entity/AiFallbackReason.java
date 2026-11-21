package com.helpdesk.modules.knowledge.entity;

public enum AiFallbackReason {
    NO_RELEVANT_KB,
    CONFLICTING_SOURCES,
    OUTDATED_OR_REVOKED_KB,
    LOW_CONFIDENCE,
    OUT_OF_AUTO_SCOPE,
    CUSTOMER_REQUESTED_HUMAN,
    CITATION_INVALID,
    AI_SERVICE_ERROR
}
