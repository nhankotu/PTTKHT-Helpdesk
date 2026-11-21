package com.helpdesk.modules.sla.repository;

import com.helpdesk.modules.sla.entity.SlaMetric;
import com.helpdesk.modules.sla.entity.SlaTrackingStatus;
import com.helpdesk.modules.sla.entity.TicketSla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketSlaRepository extends JpaRepository<TicketSla, Long> {

    List<TicketSla> findByTicketId(Long ticketId);

    Optional<TicketSla> findByTicketIdAndMetric(Long ticketId, SlaMetric metric);

    boolean existsByTicketIdAndMetric(Long ticketId, SlaMetric metric);

    List<TicketSla> findByTrackingStatus(SlaTrackingStatus status);
}
