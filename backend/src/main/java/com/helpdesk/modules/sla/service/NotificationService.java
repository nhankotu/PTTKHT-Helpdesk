package com.helpdesk.modules.sla.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.modules.sla.dto.SlaDtos.NotificationDto;
import com.helpdesk.modules.sla.entity.Notification;
import com.helpdesk.modules.sla.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationService implements NotificationPort {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void notifyAssignment(Long recipientId, Long ticketId, Long assignmentHistoryId) {
        if (recipientId == null || assignmentHistoryId == null) {
            return;
        }
        if (notificationRepository.existsByAssignmentHistoryIdAndRecipientId(assignmentHistoryId, recipientId)) {
            return;
        }
        notificationRepository.save(Notification.forAssignment(recipientId, ticketId, assignmentHistoryId, Instant.now(clock)));
    }

    @Override
    @Transactional
    public void notifySlaEvent(Long recipientId, Long ticketId, Long slaEventId) {
        if (recipientId == null || slaEventId == null) {
            return;
        }
        if (notificationRepository.existsBySlaEventIdAndRecipientId(slaEventId, recipientId)) {
            return;
        }
        notificationRepository.save(Notification.forSlaEvent(recipientId, ticketId, slaEventId, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> listForRecipient(Long recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipientId, pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional
    public NotificationDto markRead(Long recipientId, Long notificationId) {
        Notification notif = notificationRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> BusinessException.notFound("Notification", notificationId));
        notif.markRead(Instant.now(clock));
        return toDto(notificationRepository.save(notif));
    }

    @Transactional
    public int markAllRead(Long recipientId) {
        return notificationRepository.markAllReadForRecipient(recipientId, Instant.now(clock));
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(), n.getRecipientId(), n.getTicketId(), n.getSlaEventId(),
                n.getAssignmentHistoryId(), n.getDeliveryStatus(), n.getCreatedAt(), n.getSentAt(), n.getReadAt()
        );
    }
}
