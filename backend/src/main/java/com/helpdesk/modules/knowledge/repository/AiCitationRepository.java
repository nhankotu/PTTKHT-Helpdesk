package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.modules.knowledge.entity.AiCitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCitationRepository extends JpaRepository<AiCitation, Long> {

    List<AiCitation> findByAiRunIdOrderByCitationOrderAsc(Long aiRunId);
}
