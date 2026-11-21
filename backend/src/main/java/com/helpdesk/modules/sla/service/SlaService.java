package com.helpdesk.modules.sla.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.identity.entity.AppUser;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.intake.event.TicketCreatedOutboxEvent;
import com.helpdesk.modules.sla.dto.SlaDtos.EscalationActionCreateRequest;
import com.helpdesk.modules.sla.dto.SlaDtos.EscalationActionDto;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaEventDto;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaPauseDto;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaPolicyCreateRequest;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaPolicyDto;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaTargetCreateRequest;
import com.helpdesk.modules.sla.dto.SlaDtos.SlaTargetDto;
import com.helpdesk.modules.sla.dto.SlaDtos.TicketSlaDetailDto;
import com.helpdesk.modules.sla.entity.EscalationAction;
import com.helpdesk.modules.sla.entity.SlaEvent;
import com.helpdesk.modules.sla.entity.SlaEventType;
import com.helpdesk.modules.sla.entity.SlaMetric;
import com.helpdesk.modules.sla.entity.SlaPause;
import com.helpdesk.modules.sla.entity.SlaPauseReason;
import com.helpdesk.modules.sla.entity.SlaPolicy;
import com.helpdesk.modules.sla.entity.SlaPolicyTarget;
import com.helpdesk.modules.sla.entity.SlaTrackingStatus;
import com.helpdesk.modules.sla.entity.TicketSla;
import com.helpdesk.modules.sla.repository.EscalationActionRepository;
import com.helpdesk.modules.sla.repository.SlaEventRepository;
import com.helpdesk.modules.sla.repository.SlaPauseRepository;
import com.helpdesk.modules.sla.repository.SlaPolicyRepository;
import com.helpdesk.modules.sla.repository.SlaPolicyTargetRepository;
import com.helpdesk.modules.sla.repository.TicketSlaRepository;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.event.TicketPublicReplySentEvent;
import com.helpdesk.modules.ticket.event.TicketStatusChangedEvent;
import com.helpdesk.modules.ticket.service.TicketRoutingPort;
import com.helpdesk.modules.ticket.service.TicketRoutingPort.TicketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private final SlaPolicyRepository slaPolicyRepository;
    private final SlaPolicyTargetRepository slaPolicyTargetRepository;
    private final TicketSlaRepository ticketSlaRepository;
    private final SlaPauseRepository slaPauseRepository;
    private final SlaEventRepository slaEventRepository;
    private final EscalationActionRepository escalationActionRepository;
    private final TicketRoutingPort ticketRoutingPort;
    private final IdentityDirectoryService identityDirectoryService;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public SlaService(
            SlaPolicyRepository slaPolicyRepository,
            SlaPolicyTargetRepository slaPolicyTargetRepository,
            TicketSlaRepository ticketSlaRepository,
            SlaPauseRepository slaPauseRepository,
            SlaEventRepository slaEventRepository,
            EscalationActionRepository escalationActionRepository,
            TicketRoutingPort ticketRoutingPort,
            IdentityDirectoryService identityDirectoryService,
            NotificationPort notificationPort,
            Clock clock
    ) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.slaPolicyTargetRepository = slaPolicyTargetRepository;
        this.ticketSlaRepository = ticketSlaRepository;
        this.slaPauseRepository = slaPauseRepository;
        this.slaEventRepository = slaEventRepository;
        this.escalationActionRepository = escalationActionRepository;
        this.ticketRoutingPort = ticketRoutingPort;
        this.identityDirectoryService = identityDirectoryService;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    // =========================================================================
    // EVENT LISTENERS (UC02 -> UC05 & UC03 -> UC05)
    // =========================================================================

    @EventListener
    @Transactional
    public void onTicketCreated(TicketCreatedOutboxEvent event) {
        initializeTicketSla(event.ticketId());
    }

    @Transactional
    public void initializeTicketSla(Long ticketId) {
        TicketSnapshot ticket = ticketRoutingPort.requireTicketSnapshot(ticketId);
        Optional<SlaPolicy> policyOpt = slaPolicyRepository.findByPriorityAndActiveTrue(ticket.priority());
        if (policyOpt.isEmpty()) {
            log.warn("No active SLA policy found for priority={}", ticket.priority());
            return;
        }
        List<SlaPolicyTarget> targets = slaPolicyTargetRepository.findByPolicyId(policyOpt.get().getId());
        Instant startedAt = ticket.createdAt();

        for (SlaPolicyTarget target : targets) {
            if (ticketSlaRepository.existsByTicketIdAndMetric(ticketId, target.getMetric())) {
                continue;
            }
            Instant deadlineAt = startedAt.plusSeconds(target.getTargetSeconds());
            ticketSlaRepository.save(new TicketSla(
                    ticketId,
                    target.getId(),
                    target.getMetric(),
                    target.getTargetSeconds(),
                    target.getWarningRatio(),
                    startedAt,
                    deadlineAt
            ));
        }
    }

    @EventListener
    @Transactional
    public void onPublicReplySent(TicketPublicReplySentEvent event) {
        ticketSlaRepository.findByTicketIdAndMetric(event.ticketId(), SlaMetric.FIRST_RESPONSE)
                .ifPresent(frSla -> {
                    if (frSla.getTrackingStatus() == SlaTrackingStatus.RUNNING) {
                        frSla.setCompletedAt(event.sentAt());
                        frSla.setTrackingStatus(SlaTrackingStatus.COMPLETED);
                        if (event.sentAt().isAfter(frSla.getDeadlineAt()) && frSla.getBreachedAt() == null) {
                            frSla.setBreachedAt(frSla.getDeadlineAt());
                            recordSlaEventIdempotent(frSla, SlaEventType.BREACH, event.sentAt());
                        }
                        ticketSlaRepository.save(frSla);
                    }
                });
    }

    @EventListener
    @Transactional
    public void onTicketStatusChanged(TicketStatusChangedEvent event) {
        Instant now = event.occurredAt();
        if (event.newStatus() == TicketStatus.CANCELLED) {
            for (TicketSla sla : ticketSlaRepository.findByTicketId(event.ticketId())) {
                if (sla.getTrackingStatus() == SlaTrackingStatus.RUNNING || sla.getTrackingStatus() == SlaTrackingStatus.PAUSED) {
                    sla.setTrackingStatus(SlaTrackingStatus.CANCELLED);
                    ticketSlaRepository.save(sla);
                }
            }
            return;
        }

        ticketSlaRepository.findByTicketIdAndMetric(event.ticketId(), SlaMetric.RESOLUTION)
                .ifPresent(resSla -> handleResolutionStatusChange(resSla, event.previousStatus(), event.newStatus(), now));
    }

    private void handleResolutionStatusChange(TicketSla resSla, TicketStatus prev, TicketStatus next, Instant now) {
        if (next == TicketStatus.WAITING_CUSTOMER && resSla.getTrackingStatus() == SlaTrackingStatus.RUNNING) {
            resSla.setTrackingStatus(SlaTrackingStatus.PAUSED);
            ticketSlaRepository.save(resSla);
            if (slaPauseRepository.findByTicketSlaIdAndResumedAtIsNull(resSla.getId()).isEmpty()) {
                slaPauseRepository.save(new SlaPause(resSla.getId(), now, null, SlaPauseReason.WAITING_CUSTOMER));
            }
        } else if (prev == TicketStatus.WAITING_CUSTOMER && next == TicketStatus.IN_PROGRESS
                && resSla.getTrackingStatus() == SlaTrackingStatus.PAUSED) {
            slaPauseRepository.findByTicketSlaIdAndResumedAtIsNull(resSla.getId()).ifPresent(pause -> {
                pause.setResumedAt(now);
                slaPauseRepository.save(pause);
                long pausedSec = Math.max(0, Duration.between(pause.getPausedAt(), now).getSeconds());
                resSla.setDeadlineAt(resSla.getDeadlineAt().plusSeconds(pausedSec));
            });
            resSla.setTrackingStatus(SlaTrackingStatus.RUNNING);
            ticketSlaRepository.save(resSla);
        } else if (next == TicketStatus.RESOLVED && (resSla.getTrackingStatus() == SlaTrackingStatus.RUNNING
                || resSla.getTrackingStatus() == SlaTrackingStatus.PAUSED)) {
            slaPauseRepository.findByTicketSlaIdAndResumedAtIsNull(resSla.getId()).ifPresent(pause -> {
                pause.setResumedAt(now);
                slaPauseRepository.save(pause);
                long pausedSec = Math.max(0, Duration.between(pause.getPausedAt(), now).getSeconds());
                resSla.setDeadlineAt(resSla.getDeadlineAt().plusSeconds(pausedSec));
            });
            resSla.setCompletedAt(now);
            resSla.setTrackingStatus(SlaTrackingStatus.COMPLETED);
            if (now.isAfter(resSla.getDeadlineAt()) && resSla.getBreachedAt() == null) {
                resSla.setBreachedAt(resSla.getDeadlineAt());
                recordSlaEventIdempotent(resSla, SlaEventType.BREACH, now);
            }
            ticketSlaRepository.save(resSla);
        } else if (prev == TicketStatus.RESOLVED && next == TicketStatus.IN_PROGRESS
                && resSla.getTrackingStatus() == SlaTrackingStatus.COMPLETED && resSla.getCompletedAt() != null) {
            // UC05-A1: Mở lại ticket -> tạo sla_pause(RESOLVED_UNTIL_REOPEN), cộng bù deadline_at, giữ breached_at cũ
            Instant resolvedAt = resSla.getCompletedAt();
            long pausedSec = Math.max(0, Duration.between(resolvedAt, now).getSeconds());
            slaPauseRepository.save(new SlaPause(resSla.getId(), resolvedAt, now, SlaPauseReason.RESOLVED_UNTIL_REOPEN));
            resSla.setCompletedAt(null);
            resSla.setDeadlineAt(resSla.getDeadlineAt().plusSeconds(pausedSec));
            resSla.setTrackingStatus(SlaTrackingStatus.RUNNING);
            ticketSlaRepository.save(resSla);
        }
    }

    // =========================================================================
    // SCHEDULER QUÉT CẢNH BÁO 80% & QUÁ HẠN CHỐNG TRÙNG (UC05)
    // =========================================================================

    @Scheduled(fixedDelayString = "${app.sla.scan-ms:60000}")
    @Transactional
    public int scanAndTriggerAlerts() {
        Instant now = Instant.now(clock);
        List<TicketSla> runningList = ticketSlaRepository.findByTrackingStatus(SlaTrackingStatus.RUNNING);
        int eventsCreated = 0;

        for (TicketSla sla : runningList) {
            if (now.isAfter(sla.getDeadlineAt())) {
                // UC05-E1: Nếu đã quá hạn, ưu tiên BREACH và không tạo WARNING nữa
                if (sla.getBreachedAt() == null) {
                    sla.setBreachedAt(sla.getDeadlineAt());
                    ticketSlaRepository.save(sla);
                }
                if (recordSlaEventIdempotent(sla, SlaEventType.BREACH, now)) {
                    eventsCreated++;
                }
            } else {
                long remainingSec = Duration.between(now, sla.getDeadlineAt()).getSeconds();
                double warningThresholdRemaining = sla.getTargetSeconds() * (1.0 - sla.getWarningRatio().doubleValue());
                if (remainingSec <= warningThresholdRemaining) {
                    if (recordSlaEventIdempotent(sla, SlaEventType.WARNING, now)) {
                        eventsCreated++;
                    }
                }
            }
        }
        return eventsCreated;
    }

    private boolean recordSlaEventIdempotent(TicketSla sla, SlaEventType eventType, Instant now) {
        if (slaEventRepository.existsByTicketSlaIdAndEventType(sla.getId(), eventType)) {
            return false;
        }
        SlaEvent savedEvent = slaEventRepository.save(new SlaEvent(sla.getId(), eventType, now));
        TicketSnapshot ticket = ticketRoutingPort.requireTicketSnapshot(sla.getTicketId());
        Long supervisorId = identityDirectoryService.findSupervisorIdOfTeam(ticket.teamId()).orElse(null);

        if (eventType == SlaEventType.WARNING) {
            Long recipient = ticket.assignedAgentId() != null ? ticket.assignedAgentId() : supervisorId;
            if (recipient != null) {
                notificationPort.notifySlaEvent(recipient, ticket.id(), savedEvent.getId());
            }
        } else {
            if (ticket.assignedAgentId() != null) {
                notificationPort.notifySlaEvent(ticket.assignedAgentId(), ticket.id(), savedEvent.getId());
            }
            if (supervisorId != null) {
                notificationPort.notifySlaEvent(supervisorId, ticket.id(), savedEvent.getId());
            }
        }
        return true;
    }

    // =========================================================================
    // REST APIs (UC05)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<SlaPolicyDto> listPolicies() {
        return slaPolicyRepository.findAllByOrderByPriorityAscVersionDesc().stream()
                .map(p -> new SlaPolicyDto(
                        p.getId(), p.getName(), p.getPriority(), p.getVersion(), p.isActive(), p.getCreatedAt(),
                        slaPolicyTargetRepository.findByPolicyId(p.getId()).stream()
                                .map(t -> new SlaTargetDto(t.getId(), t.getMetric(), t.getTargetSeconds(), t.getWarningRatio()))
                                .toList()
                ))
                .toList();
    }

    @Transactional
    public SlaPolicyDto createNewPolicyVersion(SlaPolicyCreateRequest request) {
        Set<SlaMetric> metrics = request.targets().stream().map(SlaTargetCreateRequest::metric).collect(Collectors.toSet());
        if (!metrics.contains(SlaMetric.FIRST_RESPONSE) || !metrics.contains(SlaMetric.RESOLUTION) || request.targets().size() != 2) {
            throw BusinessException.unprocessable(
                    ErrorCode.VALIDATION_ERROR,
                    "Chính sách SLA bắt buộc phải có đúng 2 mục tiêu: FIRST_RESPONSE và RESOLUTION."
            );
        }

        slaPolicyRepository.findByPriorityAndActiveTrue(request.priority()).ifPresent(old -> {
            old.setActive(false);
            slaPolicyRepository.saveAndFlush(old);
        });

        int nextVersion = slaPolicyRepository.findFirstByPriorityOrderByVersionDesc(request.priority())
                .map(p -> p.getVersion() + 1)
                .orElse(1);

        SlaPolicy policy = slaPolicyRepository.save(
                new SlaPolicy(request.name().trim(), request.priority(), nextVersion, true, Instant.now(clock))
        );

        List<SlaTargetDto> targetDtos = request.targets().stream()
                .map(t -> {
                    SlaPolicyTarget saved = slaPolicyTargetRepository.save(
                            new SlaPolicyTarget(policy.getId(), t.metric(), t.targetSeconds(), t.warningRatio())
                    );
                    return new SlaTargetDto(saved.getId(), saved.getMetric(), saved.getTargetSeconds(), saved.getWarningRatio());
                })
                .toList();

        return new SlaPolicyDto(policy.getId(), policy.getName(), policy.getPriority(),
                policy.getVersion(), policy.isActive(), policy.getCreatedAt(), targetDtos);
    }

    @Transactional(readOnly = true)
    public List<TicketSlaDetailDto> getTicketSlaDetails(Long ticketId) {
        ticketRoutingPort.requireTicketSnapshot(ticketId);
        return ticketSlaRepository.findByTicketId(ticketId).stream()
                .map(sla -> {
                    List<SlaPauseDto> pauses = slaPauseRepository.findByTicketSlaIdOrderByPausedAtAsc(sla.getId()).stream()
                            .map(p -> new SlaPauseDto(p.getId(), p.getPausedAt(), p.getResumedAt(), p.getReason()))
                            .toList();
                    List<SlaEventDto> events = slaEventRepository.findByTicketSlaIdOrderByCreatedAtAsc(sla.getId()).stream()
                            .map(e -> toSlaEventDto(e, sla))
                            .toList();
                    return new TicketSlaDetailDto(
                            sla.getId(), sla.getTicketId(), sla.getMetric(), sla.getTargetSeconds(),
                            sla.getWarningRatio(), sla.getStartedAt(), sla.getDeadlineAt(),
                            sla.getCompletedAt(), sla.getTrackingStatus(), sla.getBreachedAt(), pauses, events
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SlaEventDto> listEscalationEvents(SlaEventType eventType, Pageable pageable) {
        Page<SlaEvent> page = (eventType != null)
                ? slaEventRepository.findByEventTypeOrderByCreatedAtDesc(eventType, pageable)
                : slaEventRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(page.map(e -> {
            TicketSla sla = ticketSlaRepository.findById(e.getTicketSlaId()).orElse(null);
            return toSlaEventDto(e, sla);
        }));
    }

    @Transactional
    public EscalationActionDto recordEscalationAction(
            Long slaEventId,
            Long handledByUserId,
            EscalationActionCreateRequest request
    ) {
        if (!slaEventRepository.existsById(slaEventId)) {
            throw BusinessException.notFound("SlaEvent", slaEventId);
        }
        EscalationAction saved = escalationActionRepository.save(new EscalationAction(
                slaEventId, handledByUserId, request.action().trim(), request.note(), Instant.now(clock)
        ));
        return toActionDto(saved);
    }

    private SlaEventDto toSlaEventDto(SlaEvent e, TicketSla sla) {
        List<EscalationActionDto> actions = escalationActionRepository.findBySlaEventIdOrderByHandledAtAsc(e.getId()).stream()
                .map(this::toActionDto)
                .toList();
        return new SlaEventDto(
                e.getId(), e.getTicketSlaId(),
                sla != null ? sla.getTicketId() : null,
                sla != null ? sla.getMetric() : null,
                e.getEventType(), e.getCreatedAt(), actions
        );
    }

    private EscalationActionDto toActionDto(EscalationAction a) {
        String name = identityDirectoryService.findUserById(a.getHandledBy())
                .map(AppUser::getFullName)
                .orElse(null);
        return new EscalationActionDto(a.getId(), a.getSlaEventId(), a.getHandledBy(), name, a.getAction(), a.getNote(), a.getHandledAt());
    }
}
