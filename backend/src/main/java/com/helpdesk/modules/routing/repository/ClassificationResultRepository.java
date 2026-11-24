package com.helpdesk.modules.routing.repository;

import com.helpdesk.modules.routing.entity.ClassificationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassificationResultRepository extends JpaRepository<ClassificationResult, Long> {

    List<ClassificationResult> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    Optional<ClassificationResult> findFirstByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
