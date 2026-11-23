package com.helpdesk.modules.knowledge.rag;

import java.util.Optional;

/**
 * Interface sinh vector embedding cho KB Chunk (RETRIEVAL_DOCUMENT) và câu truy vấn (RETRIEVAL_QUERY).
 */
public interface EmbeddingClient {

    Optional<double[]> embedDocument(String title, String content);

    Optional<double[]> embedQuery(String query);

    String embeddingRef();
}
