package com.helpdesk.modules.sla.repository;

import com.helpdesk.modules.sla.entity.SlaPolicy;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriorityAndActiveTrue(TicketPriority priority);

    Optional<SlaPolicy> findFirstByPriorityOrderByVersionDesc(TicketPriority priority);

    List<SlaPolicy> findAllByOrderByPriorityAscVersionDesc();
}
