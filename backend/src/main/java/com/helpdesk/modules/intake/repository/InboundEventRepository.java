package com.helpdesk.modules.intake.repository;

import com.helpdesk.modules.intake.entity.InboundEvent;
import com.helpdesk.modules.intake.entity.InboundProcessStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InboundEventRepository extends JpaRepository<InboundEvent, Long> {

    Optional<InboundEvent> findByChannelAccountIdAndExternalEventId(Long channelAccountId, String externalEventId);

    Optional<InboundEvent> findByIdempotencyKey(String idempotencyKey);

    Page<InboundEvent> findByProcessStatusOrderByReceivedAtDesc(InboundProcessStatus status, Pageable pageable);

    Page<InboundEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
