package com.helpdesk.modules.ticket.repository;

import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    /**
     * HARD INVARIANT: Khách hàng CHỈ được đọc tin nhắn visibility = 'PUBLIC'.
     * Lọc cứng ngay trong câu JPQL/SQL ở tầng Repository.
     */
    @Query("""
            SELECT m FROM TicketMessage m
            WHERE m.ticketId = :ticketId AND m.visibility = 'PUBLIC'
            ORDER BY m.occurredAt ASC, m.id ASC
            """)
    List<TicketMessage> findPublicMessagesByTicketId(@Param("ticketId") Long ticketId);

    List<TicketMessage> findByTicketIdOrderByOccurredAtAscIdAsc(Long ticketId);

    List<TicketMessage> findByTicketIdAndVisibilityOrderByOccurredAtAscIdAsc(Long ticketId, MessageVisibility visibility);

    Optional<TicketMessage> findByConversationIdAndExternalMessageId(Long conversationId, String externalMessageId);

    Optional<TicketMessage> findByIdAndTicketId(Long id, Long ticketId);
}
