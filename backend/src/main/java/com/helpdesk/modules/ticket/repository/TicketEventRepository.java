package com.helpdesk.modules.ticket.repository;

import com.helpdesk.modules.ticket.entity.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

    List<TicketEvent> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
