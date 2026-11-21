package com.helpdesk.modules.ticket.repository;

import com.helpdesk.modules.ticket.entity.SatisfactionRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SatisfactionRatingRepository extends JpaRepository<SatisfactionRating, Long> {

    Optional<SatisfactionRating> findByTicketId(Long ticketId);
}
