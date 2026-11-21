package com.helpdesk.modules.sla.repository;

import com.helpdesk.modules.sla.entity.SlaEvent;
import com.helpdesk.modules.sla.entity.SlaEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlaEventRepository extends JpaRepository<SlaEvent, Long> {

    boolean existsByTicketSlaIdAndEventType(Long ticketSlaId, SlaEventType eventType);

    List<SlaEvent> findByTicketSlaIdOrderByCreatedAtAsc(Long ticketSlaId);

    Page<SlaEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SlaEvent> findByEventTypeOrderByCreatedAtDesc(SlaEventType eventType, Pageable pageable);
}
