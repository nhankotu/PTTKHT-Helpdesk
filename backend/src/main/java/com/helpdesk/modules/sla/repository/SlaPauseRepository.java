package com.helpdesk.modules.sla.repository;

import com.helpdesk.modules.sla.entity.SlaPause;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPauseRepository extends JpaRepository<SlaPause, Long> {

    List<SlaPause> findByTicketSlaIdOrderByPausedAtAsc(Long ticketSlaId);

    Optional<SlaPause> findByTicketSlaIdAndResumedAtIsNull(Long ticketSlaId);
}
