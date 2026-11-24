package com.helpdesk.modules.knowledge.repository;

import com.helpdesk.modules.knowledge.entity.AiRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRunRepository extends JpaRepository<AiRun, Long> {

    List<AiRun> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
