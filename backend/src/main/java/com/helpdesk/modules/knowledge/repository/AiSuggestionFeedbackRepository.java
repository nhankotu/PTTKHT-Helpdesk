package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.modules.knowledge.entity.AiSuggestionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSuggestionFeedbackRepository extends JpaRepository<AiSuggestionFeedback, Long> {

    Optional<AiSuggestionFeedback> findByAiRunId(Long aiRunId);

    boolean existsByAiRunId(Long aiRunId);
}
