package com.helpdesk.modules.routing.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.exception.OptimisticLockConflictException;
import com.helpdesk.modules.identity.entity.Category;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.intake.event.TicketCreatedOutboxEvent;
import com.helpdesk.modules.routing.classifier.TicketClassifier;
import com.helpdesk.modules.routing.classifier.TicketClassifier.ClassificationSuggestion;
import com.helpdesk.modules.routing.dto.RoutingDtos.AssignmentResultDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.ClassificationResultDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.ManualAssignRequest;
import com.helpdesk.modules.routing.dto.RoutingDtos.ReviewQueueItemDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.RoutingRuleCreateRequest;
import com.helpdesk.modules.routing.dto.RoutingDtos.RoutingRuleDto;
import com.helpdesk.modules.routing.dto.RoutingDtos.RoutingRuleUpdateRequest;
import com.helpdesk.modules.routing.entity.AssignmentHistory;
import com.helpdesk.modules.routing.entity.AssignmentSource;
import com.helpdesk.modules.routing.entity.ClassificationOutcome;
import com.helpdesk.modules.routing.entity.ClassificationResult;
import com.helpdesk.modules.routing.entity.RoutingRule;
import com.helpdesk.modules.routing.repository.AssignmentHistoryRepository;
import com.helpdesk.modules.routing.repository.ClassificationResultRepository;
import com.helpdesk.modules.routing.repository.RoutingRuleRepository;
import com.helpdesk.modules.routing.strategy.AgentAssignmentStrategy;
import com.helpdesk.modules.sla.service.NotificationPort;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.service.TicketRoutingPort;
import com.helpdesk.modules.ticket.service.TicketRoutingPort.TicketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingRuleRepository routingRuleRepository;
    private final ClassificationResultRepository classificationResultRepository;
    private final AssignmentHistoryRepository assignmentHistoryRepository;
    private final TicketClassifier ticketClassifier;
    private final AgentAssignmentStrategy assignmentStrategy;
    private final TicketRoutingPort ticketRoutingPort;
    private final IdentityDirectoryService identityDirectoryService;
    private final NotificationPort notificationPort;
    private final Clock clock;

    public RoutingService(
            RoutingRuleRepository routingRuleRepository,
            ClassificationResultRepository classificationResultRepository,
            AssignmentHistoryRepository assignmentHistoryRepository,
            TicketClassifier ticketClassifier,
            AgentAssignmentStrategy assignmentStrategy,
            TicketRoutingPort ticketRoutingPort,
            IdentityDirectoryService identityDirectoryService,
            NotificationPort notificationPort,
            Clock clock
    ) {
        this.routingRuleRepository = routingRuleRepository;
        this.classificationResultRepository = classificationResultRepository;
        this.assignmentHistoryRepository = assignmentHistoryRepository;
        this.ticketClassifier = ticketClassifier;
        this.assignmentStrategy = assignmentStrategy;
        this.ticketRoutingPort = ticketRoutingPort;
        this.identityDirectoryService = identityDirectoryService;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onTicketCreated(TicketCreatedOutboxEvent event) {
        classifyAndAutoRoute(event.ticketId());
    }

    /**
     * Luồng tự động UC04: AI phân loại -> khớp routing_rule -> chọn Agent ít tải nhất (Strategy).
     * Nếu AI lỗi, confidence thấp hoặc không có Agent khả dụng -> Fallback an toàn sang hàng chờ Supervisor (giữ NEW).
     */
    @Transactional
    public ClassificationResultDto classifyAndAutoRoute(Long ticketId) {
        TicketSnapshot ticket = ticketRoutingPort.requireTicketSnapshot(ticketId);
        Instant now = Instant.now(clock);

        if (ticket.status() != TicketStatus.NEW || ticket.assignedAgentId() != null) {
            ClassificationResult skipped = classificationResultRepository.save(new ClassificationResult(
                    ticketId, ticket.categoryId(), null, null,
                    ClassificationOutcome.SKIPPED, "Ticket đã được phân công hoặc không còn ở trạng thái NEW", now
            ));
            return toClassificationDto(skipped);
        }

        List<Category> activeCategories = identityDirectoryService.findAllActiveCategories();
        List<String> allowedCodes = activeCategories.stream().map(Category::getCode).toList();

        ClassificationSuggestion suggestion;
        try {
            suggestion = ticketClassifier.classify(ticket.subject(), ticket.description(), allowedCodes);
        } catch (Exception ex) {
            log.warn("AI classification failed for ticketId={}: {}", ticketId, ex.getMessage());
            ClassificationResult failed = classificationResultRepository.save(new ClassificationResult(
                    ticketId, null, null, null,
                    ClassificationOutcome.FAILED, "AI_CLASSIFIER_ERROR: " + ex.getMessage(), now
            ));
            return toClassificationDto(failed);
        }

        Optional<Category> matchedCategory = identityDirectoryService.findCategoryByCode(suggestion.categoryCode());
        if (matchedCategory.isEmpty()) {
            ClassificationResult failed = classificationResultRepository.save(new ClassificationResult(
                    ticketId, null, suggestion.confidence(), suggestion.modelVersion(),
                    ClassificationOutcome.REVIEW_REQUIRED, "Danh mục AI đề xuất không hợp lệ: " + suggestion.categoryCode(), now
            ));
            return toClassificationDto(failed);
        }

        Long categoryId = matchedCategory.get().getId();
        Optional<RoutingRule> ruleOpt = routingRuleRepository.findByCategoryIdAndActiveTrue(categoryId);
        if (ruleOpt.isEmpty() || !ruleOpt.get().isAutoAssignEnabled()) {
            ClassificationResult review = classificationResultRepository.save(new ClassificationResult(
                    ticketId, categoryId, suggestion.confidence(), suggestion.modelVersion(),
                    ClassificationOutcome.REVIEW_REQUIRED, "Không có luật định tuyến tự động đang bật cho danh mục này", now
            ));
            return toClassificationDto(review);
        }

        RoutingRule rule = ruleOpt.get();
        if (suggestion.confidence() == null || suggestion.confidence().compareTo(rule.getConfidenceThreshold()) < 0) {
            ClassificationResult review = classificationResultRepository.save(new ClassificationResult(
                    ticketId, categoryId, suggestion.confidence(), suggestion.modelVersion(),
                    ClassificationOutcome.REVIEW_REQUIRED, "Độ tin cậy AI thấp hơn ngưỡng định tuyến (" + rule.getConfidenceThreshold() + ")", now
            ));
            return toClassificationDto(review);
        }

        List<Long> availableAgents = identityDirectoryService.findAvailableActiveAgentIdsInTeam(rule.getTeamId());
        if (availableAgents.isEmpty()) {
            ClassificationResult review = classificationResultRepository.save(new ClassificationResult(
                    ticketId, categoryId, suggestion.confidence(), suggestion.modelVersion(),
                    ClassificationOutcome.REVIEW_REQUIRED, "Không có nhân viên khả dụng trong nhóm đích (teamId=" + rule.getTeamId() + ")", now
            ));
            return toClassificationDto(review);
        }

        Map<Long, Long> openCounts = ticketRoutingPort.countOpenTicketsByAgents(availableAgents);
        Map<Long, Instant> lastAutoAssigned = new HashMap<>();
        for (Object[] row : assignmentHistoryRepository.findLastAutoAssignedAtByAgentIds(availableAgents)) {
            lastAutoAssigned.put((Long) row[0], (Instant) row[1]);
        }

        Optional<Long> selectedAgentOpt = assignmentStrategy.selectAgent(availableAgents, openCounts, lastAutoAssigned);
        if (selectedAgentOpt.isEmpty()) {
            ClassificationResult review = classificationResultRepository.save(new ClassificationResult(
                    ticketId, categoryId, suggestion.confidence(), suggestion.modelVersion(),
                    ClassificationOutcome.REVIEW_REQUIRED, "Chiến lược phân công không chọn được nhân viên phù hợp", now
            ));
            return toClassificationDto(review);
        }

        Long selectedAgentId = selectedAgentOpt.get();
        try {
            ticketRoutingPort.applyAssignment(
                    ticketId, ticket.version(), categoryId, rule.getTeamId(), selectedAgentId,
                    null, "Tự động phân công qua AI (" + suggestion.modelVersion() + ")"
            );
        } catch (OptimisticLockConflictException ex) {
            ClassificationResult skipped = classificationResultRepository.save(new ClassificationResult(
                    ticketId, categoryId, suggestion.confidence(), suggestion.modelVersion(),
                    ClassificationOutcome.SKIPPED, "Ticket đã được chỉnh sửa thủ công trước khi AI hoàn tất", now
            ));
            return toClassificationDto(skipped);
        }

        ClassificationResult applied = classificationResultRepository.save(new ClassificationResult(
                ticketId, categoryId, suggestion.confidence(), suggestion.modelVersion(),
                ClassificationOutcome.APPLIED, null, now
        ));
        AssignmentHistory history = assignmentHistoryRepository.save(new AssignmentHistory(
                ticketId, categoryId, rule.getTeamId(), selectedAgentId, null,
                AssignmentSource.AUTO, suggestion.reason(), now
        ));
        notificationPort.notifyAssignment(selectedAgentId, ticketId, history.getId());

        return toClassificationDto(applied);
    }

    @Transactional
    public AssignmentResultDto assignManually(Long ticketId, Long supervisorId, ManualAssignRequest request) {
        identityDirectoryService.requireCategoryById(request.categoryId());
        identityDirectoryService.requireTeamById(request.teamId());
        if (!identityDirectoryService.isUserMemberOfTeam(request.teamId(), request.agentId())) {
            throw BusinessException.unprocessable(
                    ErrorCode.AGENT_NOT_IN_TEAM,
                    "Nhân viên được chọn không thuộc nhóm hỗ trợ đích."
            );
        }

        TicketSnapshot updated = ticketRoutingPort.applyAssignment(
                ticketId, request.version(), request.categoryId(), request.teamId(),
                request.agentId(), supervisorId, request.reason().trim()
        );
        Instant now = Instant.now(clock);
        AssignmentHistory history = assignmentHistoryRepository.save(new AssignmentHistory(
                ticketId, request.categoryId(), request.teamId(), request.agentId(),
                supervisorId, AssignmentSource.MANUAL, request.reason().trim(), now
        ));
        notificationPort.notifyAssignment(request.agentId(), ticketId, history.getId());

        return new AssignmentResultDto(
                updated.id(), updated.status(), updated.categoryId(), updated.teamId(),
                updated.assignedAgentId(), AssignmentSource.MANUAL, updated.version(), now
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewQueueItemDto> listReviewQueue(Pageable pageable) {
        PageResponse<TicketSnapshot> tickets = ticketRoutingPort.listUnassignedNewTickets(pageable);
        List<ReviewQueueItemDto> items = tickets.items().stream()
                .map(t -> {
                    ClassificationResultDto latest = classificationResultRepository
                            .findFirstByTicketIdOrderByCreatedAtDesc(t.id())
                            .map(this::toClassificationDto)
                            .orElse(null);
                    return new ReviewQueueItemDto(
                            t.id(), t.ticketCode(), t.subject(), t.priority(),
                            t.status(), t.version(), t.createdAt(), latest
                    );
                })
                .toList();
        return new PageResponse<>(
                items, tickets.page(), tickets.size(),
                tickets.totalElements(), tickets.totalPages(), tickets.first(), tickets.last()
        );
    }

    @Transactional(readOnly = true)
    public List<ClassificationResultDto> listTicketClassifications(Long ticketId) {
        return classificationResultRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(this::toClassificationDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoutingRuleDto> listRules() {
        return routingRuleRepository.findAllByOrderByIdAsc().stream()
                .map(this::toRuleDto)
                .toList();
    }

    @Transactional
    public RoutingRuleDto createRule(RoutingRuleCreateRequest request) {
        identityDirectoryService.requireCategoryById(request.categoryId());
        identityDirectoryService.requireTeamById(request.teamId());
        if (request.isActive() && routingRuleRepository.findByCategoryIdAndActiveTrue(request.categoryId()).isPresent()) {
            throw BusinessException.conflict(
                    ErrorCode.ACTIVE_RULE_CONFLICT,
                    "Danh mục này đã có một luật định tuyến đang hoạt động (is_active = TRUE)."
            );
        }
        RoutingRule saved = routingRuleRepository.save(new RoutingRule(
                request.categoryId(), request.teamId(), request.confidenceThreshold(),
                request.autoAssignEnabled(), request.isActive()
        ));
        return toRuleDto(saved);
    }

    @Transactional
    public RoutingRuleDto updateRule(Long ruleId, RoutingRuleUpdateRequest request) {
        RoutingRule rule = routingRuleRepository.findById(ruleId)
                .orElseThrow(() -> BusinessException.notFound("RoutingRule", ruleId));
        if (request.teamId() != null) {
            identityDirectoryService.requireTeamById(request.teamId());
            rule.setTeamId(request.teamId());
        }
        if (request.confidenceThreshold() != null) {
            rule.setConfidenceThreshold(request.confidenceThreshold());
        }
        if (request.autoAssignEnabled() != null) {
            rule.setAutoAssignEnabled(request.autoAssignEnabled());
        }
        if (request.isActive() != null) {
            if (request.isActive() && routingRuleRepository.existsByCategoryIdAndActiveTrueAndIdNot(rule.getCategoryId(), ruleId)) {
                throw BusinessException.conflict(
                        ErrorCode.ACTIVE_RULE_CONFLICT,
                        "Danh mục này đã có một luật định tuyến khác đang hoạt động."
                );
            }
            rule.setActive(request.isActive());
        }
        return toRuleDto(routingRuleRepository.save(rule));
    }

    private RoutingRuleDto toRuleDto(RoutingRule r) {
        return new RoutingRuleDto(r.getId(), r.getCategoryId(), r.getTeamId(),
                r.getConfidenceThreshold(), r.isAutoAssignEnabled(), r.isActive());
    }

    private ClassificationResultDto toClassificationDto(ClassificationResult c) {
        return new ClassificationResultDto(
                c.getId(), c.getTicketId(), c.getSuggestedCategoryId(), c.getConfidence(),
                c.getModelVersion(), c.getOutcome(), c.getFailureReason(), c.getCreatedAt()
        );
    }
}
