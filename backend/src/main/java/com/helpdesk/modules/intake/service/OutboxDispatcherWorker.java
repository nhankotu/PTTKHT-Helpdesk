package com.helpdesk.modules.intake.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.modules.intake.entity.OutboxEvent;
import com.helpdesk.modules.intake.event.TicketCreatedOutboxEvent;
import com.helpdesk.modules.intake.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Transactional Outbox Worker: đọc batch PENDING bằng SELECT ... FOR UPDATE SKIP LOCKED (MySQL 8.0)
 * và phát sự kiện tới các consumer (UC04 Routing, UC05 SLA).
 */
@Component
public class OutboxDispatcherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcherWorker.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxDispatcherWorker(
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:3000}")
    @Transactional
    public void pollAndDispatch() {
        List<OutboxEvent> batch = outboxEventRepository.lockPendingBatch(BATCH_SIZE);
        Instant now = Instant.now(clock);
        for (OutboxEvent event : batch) {
            try {
                if ("TicketCreated".equals(event.getEventType())) {
                    JsonNode node = objectMapper.readTree(event.getPayload());
                    Long ticketId = node.get("ticketId").asLong();
                    String ticketCode = node.path("ticketCode").asText("");
                    Instant createdAt = Instant.parse(node.path("createdAt").asText(now.toString()));
                    eventPublisher.publishEvent(new TicketCreatedOutboxEvent(event.getId(), ticketId, ticketCode, createdAt));
                }
                event.markPublished(now);
            } catch (Exception ex) {
                log.error("Failed to dispatch outbox event id={}: {}", event.getId(), ex.getMessage());
                event.incrementRetry(event.getRetryCount() + 1 >= MAX_RETRIES);
            }
            outboxEventRepository.save(event);
        }
    }
}
