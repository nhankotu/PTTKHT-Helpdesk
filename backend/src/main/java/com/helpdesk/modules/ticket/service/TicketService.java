package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.dto.PageResponse;
import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.exception.OptimisticLockConflictException;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.identity.entity.AppUser;
import com.helpdesk.modules.identity.service.IdentityDirectoryService;
import com.helpdesk.modules.ticket.adapter.OutboundChannelAdapter;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CsatResponse;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CsatSubmitRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CsatSummaryDto;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerCloseRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerMessageDto;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerReopenRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerReplyRequest;
import com.helpdesk.modules.ticket.dto.CustomerTicketDtos.CustomerTicketDetailDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.MetadataUpdateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageCreateResponse;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffMessageDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StaffTicketDetailDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.StatusUpdateRequest;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.TicketEventDto;
import com.helpdesk.modules.ticket.dto.StaffTicketDtos.TransferTicketRequest;
import com.helpdesk.modules.ticket.entity.MessageDeliveryStatus;
import com.helpdesk.modules.ticket.entity.MessageDirection;
import com.helpdesk.modules.ticket.entity.MessageSenderType;
import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.entity.SatisfactionRating;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketEvent;
import com.helpdesk.modules.ticket.entity.TicketEventAction;
import com.helpdesk.modules.ticket.entity.TicketMessage;
import com.helpdesk.modules.ticket.entity.TicketPriority;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import com.helpdesk.modules.ticket.event.TicketPublicReplySentEvent;
import com.helpdesk.modules.ticket.event.TicketStatusChangedEvent;
import com.helpdesk.modules.ticket.repository.SatisfactionRatingRepository;
import com.helpdesk.modules.ticket.repository.TicketEventRepository;
import com.helpdesk.modules.ticket.repository.TicketMessageRepository;
import com.helpdesk.modules.ticket.repository.TicketRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TicketService implements TicketIntakePort {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketEventRepository ticketEventRepository;
    private final SatisfactionRatingRepository satisfactionRatingRepository;
    private final TicketStateMachineGuard stateMachineGuard;
    private final OutboundChannelAdapter outboundChannelAdapter;
    private final IdentityDirectoryService identityDirectoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TicketService(
            TicketRepository ticketRepository,
            TicketMessageRepository ticketMessageRepository,
            TicketEventRepository ticketEventRepository,
            SatisfactionRatingRepository satisfactionRatingRepository,
            TicketStateMachineGuard stateMachineGuard,
            OutboundChannelAdapter outboundChannelAdapter,
            IdentityDirectoryService identityDirectoryService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.satisfactionRatingRepository = satisfactionRatingRepository;
        this.stateMachineGuard = stateMachineGuard;
        this.outboundChannelAdapter = outboundChannelAdapter;
        this.identityDirectoryService = identityDirectoryService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // =========================================================================
    // PORT FOR INTAKE MODULE (UC01 - UC02)
    // =========================================================================

    @Override
    @Transactional
    public IntakeTicketResult ingestInboundMessage(IntakeTicketCommand cmd) {
        Instant now = cmd.occurredAt() != null ? cmd.occurredAt() : Instant.now(clock);

        if (cmd.externalMessageId() != null) {
            Optional<TicketMessage> existingMsg = ticketMessageRepository
                    .findByConversationIdAndExternalMessageId(cmd.conversationId(), cmd.externalMessageId());
            if (existingMsg.isPresent()) {
                TicketMessage msg = existingMsg.get();
                Ticket existingTicket = ticketRepository.findById(msg.getTicketId())
                        .orElseThrow(() -> BusinessException.notFound("Ticket", msg.getTicketId()));
                return new IntakeTicketResult(existingTicket.getId(), existingTicket.getTicketCode(), msg.getId(), false);
            }
        }

        Optional<Ticket> latestTicketOpt = ticketRepository.findFirstByConversationIdOrderByCreatedAtDesc(cmd.conversationId());
        Ticket ticket;
        boolean createdNew;

        if (latestTicketOpt.isEmpty() || latestTicketOpt.get().getStatus().isTerminal()) {
            Long parentId = latestTicketOpt
                    .filter(t -> t.getStatus() == TicketStatus.CLOSED)
                    .map(Ticket::getId)
                    .orElse(null);

            ticket = new Ticket(
                    generateTicketCode(now),
                    cmd.conversationId(),
                    cmd.customerId(),
                    parentId,
                    cmd.channelId(),
                    cmd.categoryId(),
                    cmd.subject(),
                    cmd.body(),
                    now
            );
            ticket = ticketRepository.save(ticket);
            recordEvent(ticket.getId(), null, TicketEventAction.CREATED, "status", null, TicketStatus.NEW.name(), "Tiếp nhận từ kênh", now);
            createdNew = true;
        } else {
            ticket = latestTicketOpt.get();
            createdNew = false;
            if (ticket.getStatus() == TicketStatus.WAITING_CUSTOMER || ticket.getStatus() == TicketStatus.RESOLVED) {
                TicketStatus oldStatus = ticket.getStatus();
                ticket.setStatus(TicketStatus.IN_PROGRESS);
                ticketRepository.save(ticket);
                TicketEventAction action = oldStatus == TicketStatus.RESOLVED ? TicketEventAction.REOPENED : TicketEventAction.STATUS_CHANGE;
                recordEvent(ticket.getId(), null, action, "status", oldStatus.name(), TicketStatus.IN_PROGRESS.name(), "Khách hàng phản hồi mới", now);
                eventPublisher.publishEvent(new TicketStatusChangedEvent(ticket.getId(), oldStatus, TicketStatus.IN_PROGRESS, now));
            }
        }

        TicketMessage message = new TicketMessage(
                cmd.conversationId(),
                ticket.getId(),
                null,
                MessageSenderType.CUSTOMER,
                MessageDirection.INBOUND,
                MessageVisibility.PUBLIC,
                false,
                cmd.externalMessageId(),
                cmd.body(),
                MessageDeliveryStatus.DELIVERED,
                now
        );
        message = ticketMessageRepository.save(message);

        return new IntakeTicketResult(ticket.getId(), ticket.getTicketCode(), message.getId(), createdNew);
    }

    // =========================================================================
    // CUSTOMER PORTAL (UC03 — STRICTLY PUBLIC & OWNERSHIP-CHECKED)
    // =========================================================================

    @Transactional(readOnly = true)
    public PageResponse<CustomerTicketDetailDto> listCustomerTickets(
            HelpdeskUserPrincipal principal,
            TicketStatus status,
            Pageable pageable
    ) {
        Long customerId = requireCustomerId(principal);
        Page<Ticket> page = (status != null)
                ? ticketRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status, pageable)
                : ticketRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        return PageResponse.from(page.map(this::toCustomerDetailDto));
    }

    @Transactional(readOnly = true)
    public CustomerTicketDetailDto getCustomerTicketDetail(HelpdeskUserPrincipal principal, Long ticketId) {
        Ticket ticket = requireOwnedCustomerTicket(principal, ticketId);
        return toCustomerDetailDto(ticket);
    }

    @Transactional(readOnly = true)
    public List<CustomerMessageDto> listCustomerPublicMessages(HelpdeskUserPrincipal principal, Long ticketId) {
        requireOwnedCustomerTicket(principal, ticketId);
        return ticketMessageRepository.findPublicMessagesByTicketId(ticketId).stream()
                .map(this::toCustomerMessageDto)
                .toList();
    }

    @Transactional
    public CustomerMessageDto addCustomerReply(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            CustomerReplyRequest request
    ) {
        Ticket ticket = requireOwnedCustomerTicket(principal, ticketId);
        if (ticket.getStatus().isTerminal()) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Ticket đã đóng hoặc hủy, vui lòng tạo yêu cầu hỗ trợ mới."
            );
        }

        Instant now = Instant.now(clock);
        if (ticket.getStatus() == TicketStatus.WAITING_CUSTOMER || ticket.getStatus() == TicketStatus.RESOLVED) {
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
            TicketEventAction action = oldStatus == TicketStatus.RESOLVED ? TicketEventAction.REOPENED : TicketEventAction.STATUS_CHANGE;
            recordEvent(ticket.getId(), principal.userId(), action, "status", oldStatus.name(), TicketStatus.IN_PROGRESS.name(), "Khách hàng bổ sung thông tin", now);
            eventPublisher.publishEvent(new TicketStatusChangedEvent(ticket.getId(), oldStatus, TicketStatus.IN_PROGRESS, now));
        }

        TicketMessage message = new TicketMessage(
                ticket.getConversationId(),
                ticket.getId(),
                principal.userId(),
                MessageSenderType.CUSTOMER,
                MessageDirection.INBOUND,
                MessageVisibility.PUBLIC,
                false,
                null,
                request.body().trim(),
                MessageDeliveryStatus.DELIVERED,
                now
        );
        return toCustomerMessageDto(ticketMessageRepository.save(message));
    }

    @Transactional
    public CustomerTicketDetailDto confirmCloseByCustomer(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            CustomerCloseRequest request
    ) {
        Ticket ticket = requireOwnedCustomerTicket(principal, ticketId);
        verifyVersion(ticket, request.version());
        stateMachineGuard.validateTransition(ticket.getStatus(), TicketStatus.CLOSED, null, null);

        Instant now = Instant.now(clock);
        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(now);
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        recordEvent(saved.getId(), principal.userId(), TicketEventAction.STATUS_CHANGE, "status", oldStatus.name(), TicketStatus.CLOSED.name(), "Khách hàng xác nhận đóng ticket", now);
        eventPublisher.publishEvent(new TicketStatusChangedEvent(saved.getId(), oldStatus, TicketStatus.CLOSED, now));
        return toCustomerDetailDto(saved);
    }

    @Transactional
    public CustomerTicketDetailDto reopenByCustomer(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            CustomerReopenRequest request
    ) {
        Ticket ticket = requireOwnedCustomerTicket(principal, ticketId);
        verifyVersion(ticket, request.version());
        if (ticket.getStatus() != TicketStatus.RESOLVED) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ có thể mở lại ticket đang ở trạng thái RESOLVED."
            );
        }

        Instant now = Instant.now(clock);
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        recordEvent(saved.getId(), principal.userId(), TicketEventAction.REOPENED, "status", TicketStatus.RESOLVED.name(), TicketStatus.IN_PROGRESS.name(), request.reason().trim(), now);
        eventPublisher.publishEvent(new TicketStatusChangedEvent(saved.getId(), TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS, now));
        return toCustomerDetailDto(saved);
    }

    @Transactional
    public CsatResponse submitCsat(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            CsatSubmitRequest request
    ) {
        Ticket ticket = requireOwnedCustomerTicket(principal, ticketId);
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ có thể đánh giá CSAT khi ticket đã ở trạng thái RESOLVED hoặc CLOSED."
            );
        }

        Instant now = Instant.now(clock);
        SatisfactionRating rating = satisfactionRatingRepository.findByTicketId(ticketId)
                .orElseGet(() -> new SatisfactionRating(ticketId, ticket.getCustomerId(), now));
        if (rating.getSubmittedAt() != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_RESOURCE, "Bạn đã gửi đánh giá CSAT cho ticket này.");
        }

        rating.submit(request.score().byteValue(), request.comment(), now);
        SatisfactionRating saved = satisfactionRatingRepository.save(rating);
        return new CsatResponse(saved.getTicketId(), saved.getScore().intValue(), saved.getComment(), saved.getSubmittedAt());
    }

    // =========================================================================
    // STAFF WORKSPACE (UC03 — AGENT, SUPERVISOR, ADMIN)
    // =========================================================================

    @Transactional(readOnly = true)
    public PageResponse<StaffTicketDetailDto> searchStaffTickets(
            HelpdeskUserPrincipal principal,
            TicketStatus status,
            TicketPriority priority,
            Long categoryId,
            Long teamId,
            Long assignedAgentId,
            boolean unassignedOnly,
            String keyword,
            Pageable pageable
    ) {
        boolean restrictScope = !principal.hasRole("ADMIN");
        Collection<Long> allowedTeams = principal.teamIds().isEmpty() ? List.of(-1L) : principal.teamIds();
        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<Ticket> page = ticketRepository.searchStaffTickets(
                status, priority, categoryId, teamId, assignedAgentId, unassignedOnly,
                trimmedKeyword, restrictScope, principal.userId(), allowedTeams, pageable
        );
        return PageResponse.from(page.map(this::toStaffDetailDto));
    }

    @Transactional(readOnly = true)
    public StaffTicketDetailDto getStaffTicketDetail(HelpdeskUserPrincipal principal, Long ticketId) {
        Ticket ticket = requireAccessibleStaffTicket(principal, ticketId);
        return toStaffDetailDto(ticket);
    }

    @Transactional(readOnly = true)
    public List<StaffMessageDto> listStaffMessages(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            MessageVisibility visibilityFilter
    ) {
        requireAccessibleStaffTicket(principal, ticketId);
        List<TicketMessage> messages = (visibilityFilter == null)
                ? ticketMessageRepository.findByTicketIdOrderByOccurredAtAscIdAsc(ticketId)
                : ticketMessageRepository.findByTicketIdAndVisibilityOrderByOccurredAtAscIdAsc(ticketId, visibilityFilter);
        return messages.stream().map(this::toStaffMessageDto).toList();
    }

    @Transactional
    public StaffMessageCreateResponse postStaffMessage(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            StaffMessageCreateRequest request
    ) {
        Ticket ticket = requireAccessibleStaffTicket(principal, ticketId);
        if (ticket.getStatus().isTerminal()) {
            throw BusinessException.unprocessable(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Không thể thêm tin nhắn vào ticket đã đóng hoặc đã hủy."
            );
        }

        boolean wantsStatusChange = request.targetStatus() != null && request.targetStatus() != ticket.getStatus();
        if (wantsStatusChange) {
            if (request.version() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "Bắt buộc truyền version khi đổi trạng thái ticket.");
            }
            verifyVersion(ticket, request.version());
            stateMachineGuard.validateTransition(ticket.getStatus(), request.targetStatus(), request.resolutionCode(), null);
        }

        Instant now = Instant.now(clock);
        MessageSenderType senderType = principal.hasRole("SUPERVISOR") ? MessageSenderType.SUPERVISOR : MessageSenderType.AGENT;

        if (request.visibility() == MessageVisibility.INTERNAL) {
            TicketMessage internalNote = new TicketMessage(
                    ticket.getConversationId(),
                    ticket.getId(),
                    principal.userId(),
                    senderType,
                    MessageDirection.INTERNAL,
                    MessageVisibility.INTERNAL,
                    false,
                    null,
                    request.body().trim(),
                    MessageDeliveryStatus.SENT,
                    now
            );
            internalNote = ticketMessageRepository.save(internalNote);
            return new StaffMessageCreateResponse(toStaffMessageDto(internalNote), ticket.getStatus(), ticket.getVersion());
        }

        // PUBLIC OUTBOUND reply -> đẩy qua ChannelAdapter (UC03)
        TicketMessage outbound = new TicketMessage(
                ticket.getConversationId(),
                ticket.getId(),
                principal.userId(),
                senderType,
                MessageDirection.OUTBOUND,
                MessageVisibility.PUBLIC,
                false,
                null,
                request.body().trim(),
                MessageDeliveryStatus.PENDING,
                now
        );
        outbound = ticketMessageRepository.save(outbound);
        MessageDeliveryStatus deliveryStatus = outboundChannelAdapter.deliver(ticket, outbound);
        outbound.setDeliveryStatus(deliveryStatus);
        outbound = ticketMessageRepository.save(outbound);

        // UC03-E1: Gửi kênh lỗi -> KHÔNG đổi trạng thái & KHÔNG tính FIRST_RESPONSE SLA
        if (deliveryStatus == MessageDeliveryStatus.FAILED) {
            return new StaffMessageCreateResponse(toStaffMessageDto(outbound), ticket.getStatus(), ticket.getVersion());
        }

        eventPublisher.publishEvent(new TicketPublicReplySentEvent(ticket.getId(), outbound.getId(), senderType, now));

        if (wantsStatusChange) {
            applyStatusTransition(ticket, principal.userId(), request.targetStatus(), request.resolutionCode(), null, now);
            ticket = ticketRepository.saveAndFlush(ticket);
        }

        return new StaffMessageCreateResponse(toStaffMessageDto(outbound), ticket.getStatus(), ticket.getVersion());
    }

    @Transactional
    public StaffMessageDto retryFailedMessage(HelpdeskUserPrincipal principal, Long ticketId, Long messageId) {
        Ticket ticket = requireAccessibleStaffTicket(principal, ticketId);
        TicketMessage message = ticketMessageRepository.findByIdAndTicketId(messageId, ticketId)
                .orElseThrow(() -> BusinessException.notFound("TicketMessage", messageId));

        if (message.getVisibility() != MessageVisibility.PUBLIC || message.getDeliveryStatus() != MessageDeliveryStatus.FAILED) {
            throw BusinessException.unprocessable(
                    ErrorCode.VALIDATION_ERROR,
                    "Chỉ hỗ trợ thử gửi lại các tin nhắn PUBLIC có trạng thái FAILED."
            );
        }

        MessageDeliveryStatus deliveryStatus = outboundChannelAdapter.deliver(ticket, message);
        message.setDeliveryStatus(deliveryStatus);
        TicketMessage saved = ticketMessageRepository.save(message);

        if (deliveryStatus == MessageDeliveryStatus.SENT || deliveryStatus == MessageDeliveryStatus.DELIVERED) {
            eventPublisher.publishEvent(new TicketPublicReplySentEvent(
                    ticket.getId(), saved.getId(), saved.getSenderType(), Instant.now(clock)
            ));
        }
        return toStaffMessageDto(saved);
    }

    @Transactional
    public StaffTicketDetailDto updateStatus(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            StatusUpdateRequest request
    ) {
        Ticket ticket = requireAccessibleStaffTicket(principal, ticketId);
        verifyVersion(ticket, request.version());
        stateMachineGuard.validateTransition(ticket.getStatus(), request.targetStatus(), request.resolutionCode(), request.cancelReason());

        Instant now = Instant.now(clock);
        applyStatusTransition(ticket, principal.userId(), request.targetStatus(), request.resolutionCode(), request.cancelReason(), now);
        Ticket saved = ticketRepository.saveAndFlush(ticket);
        return toStaffDetailDto(saved);
    }

    @Transactional
    public StaffTicketDetailDto updateMetadata(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            MetadataUpdateRequest request
    ) {
        Ticket ticket = requireAccessibleStaffTicket(principal, ticketId);
        verifyVersion(ticket, request.version());
        Instant now = Instant.now(clock);

        if (request.priority() != null && request.priority() != ticket.getPriority()) {
            if (!principal.hasRole("SUPERVISOR") && !principal.hasRole("ADMIN")) {
                throw BusinessException.forbidden(ErrorCode.FORBIDDEN, "Chỉ Supervisor hoặc Admin được thay đổi mức ưu tiên.");
            }
            String oldVal = ticket.getPriority().name();
            ticket.setPriority(request.priority());
            recordEvent(ticket.getId(), principal.userId(), TicketEventAction.PRIORITY_CHANGE, "priority", oldVal, request.priority().name(), null, now);
        }

        if (request.categoryId() != null && !request.categoryId().equals(ticket.getCategoryId())) {
            identityDirectoryService.requireCategoryById(request.categoryId());
            String oldVal = ticket.getCategoryId() != null ? ticket.getCategoryId().toString() : null;
            ticket.setCategoryId(request.categoryId());
            recordEvent(ticket.getId(), principal.userId(), TicketEventAction.CATEGORY_CHANGE, "category_id", oldVal, request.categoryId().toString(), null, now);
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        return toStaffDetailDto(saved);
    }

    @Transactional
    public StaffTicketDetailDto transferTicket(
            HelpdeskUserPrincipal principal,
            Long ticketId,
            TransferTicketRequest request
    ) {
        Ticket ticket = requireAccessibleStaffTicket(principal, ticketId);
        verifyVersion(ticket, request.version());
        identityDirectoryService.requireTeamById(request.teamId());

        if (request.assignedAgentId() != null
                && !identityDirectoryService.isUserMemberOfTeam(request.teamId(), request.assignedAgentId())) {
            throw BusinessException.unprocessable(
                    ErrorCode.AGENT_NOT_IN_TEAM,
                    "Nhân viên được chọn không thuộc nhóm hỗ trợ đích."
            );
        }

        Instant now = Instant.now(clock);
        String oldAssignment = "team=" + ticket.getTeamId() + ",agent=" + ticket.getAssignedAgentId();
        ticket.setTeamId(request.teamId());
        ticket.setAssignedAgentId(request.assignedAgentId());
        if (ticket.getStatus() == TicketStatus.NEW && request.assignedAgentId() != null) {
            ticket.setStatus(TicketStatus.ASSIGNED);
        }

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        String newAssignment = "team=" + saved.getTeamId() + ",agent=" + saved.getAssignedAgentId();
        recordEvent(saved.getId(), principal.userId(), TicketEventAction.ASSIGNMENT_CHANGE, "assignment", oldAssignment, newAssignment, request.reason().trim(), now);
        return toStaffDetailDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketEventDto> listTicketEvents(HelpdeskUserPrincipal principal, Long ticketId) {
        requireAccessibleStaffTicket(principal, ticketId);
        return ticketEventRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream()
                .map(e -> new TicketEventDto(
                        e.getId(), e.getTicketId(), e.getActorId(), e.getActionType(),
                        e.getFieldName(), e.getOldValue(), e.getNewValue(), e.getReason(), e.getCreatedAt()
                ))
                .toList();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void applyStatusTransition(
            Ticket ticket,
            Long actorId,
            TicketStatus targetStatus,
            String resolutionCode,
            String cancelReason,
            Instant now
    ) {
        TicketStatus oldStatus = ticket.getStatus();
        if (oldStatus == targetStatus) {
            return;
        }
        ticket.setStatus(targetStatus);
        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setResolutionCode(resolutionCode.trim());
            ticket.setResolvedAt(now);
            satisfactionRatingRepository.findByTicketId(ticket.getId())
                    .orElseGet(() -> satisfactionRatingRepository.save(new SatisfactionRating(ticket.getId(), ticket.getCustomerId(), now)));
        } else if (targetStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
        } else if (targetStatus == TicketStatus.CANCELLED) {
            ticket.setCancelReason(cancelReason.trim());
        }

        TicketEventAction action = targetStatus == TicketStatus.CANCELLED
                ? TicketEventAction.CANCELLED
                : TicketEventAction.STATUS_CHANGE;
        String reason = targetStatus == TicketStatus.CANCELLED ? cancelReason.trim() : resolutionCode;
        recordEvent(ticket.getId(), actorId, action, "status", oldStatus.name(), targetStatus.name(), reason, now);
        eventPublisher.publishEvent(new TicketStatusChangedEvent(ticket.getId(), oldStatus, targetStatus, now));
    }

    private void verifyVersion(Ticket ticket, Integer expectedVersion) {
        if (expectedVersion == null || ticket.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(ticket.getVersion());
        }
    }

    private Long requireCustomerId(HelpdeskUserPrincipal principal) {
        if (principal.customerId() == null) {
            throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Tài khoản chưa liên kết hồ sơ khách hàng.");
        }
        return principal.customerId();
    }

    private Ticket requireOwnedCustomerTicket(HelpdeskUserPrincipal principal, Long ticketId) {
        Long customerId = requireCustomerId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket", ticketId));
        if (!customerId.equals(ticket.getCustomerId())) {
            throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Bạn không có quyền truy cập ticket này.");
        }
        return ticket;
    }

    private Ticket requireAccessibleStaffTicket(HelpdeskUserPrincipal principal, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> BusinessException.notFound("Ticket", ticketId));
        if (principal.hasRole("ADMIN")) {
            return ticket;
        }
        boolean isAssigned = principal.userId().equals(ticket.getAssignedAgentId());
        boolean inTeam = ticket.getTeamId() != null && principal.isInTeam(ticket.getTeamId());
        boolean unassigned = ticket.getTeamId() == null && ticket.getAssignedAgentId() == null;
        if (!isAssigned && !inTeam && !unassigned) {
            throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Ticket nằm ngoài phạm vi nhóm bạn được phân quyền.");
        }
        return ticket;
    }

    private void recordEvent(
            Long ticketId, Long actorId, TicketEventAction action, String fieldName,
            String oldValue, String newValue, String reason, Instant createdAt
    ) {
        ticketEventRepository.save(new TicketEvent(ticketId, actorId, action, fieldName, oldValue, newValue, reason, createdAt));
    }

    private String generateTicketCode(Instant now) {
        int year = now.atZone(ZoneOffset.UTC).getYear();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "HD-" + year + "-" + suffix;
    }

    private CustomerTicketDetailDto toCustomerDetailDto(Ticket t) {
        String agentName = identityDirectoryService.findUserById(t.getAssignedAgentId())
                .map(AppUser::getFullName)
                .orElse(null);
        Optional<SatisfactionRating> ratingOpt = satisfactionRatingRepository.findByTicketId(t.getId());
        boolean eligible = t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED;
        CsatSummaryDto csat = ratingOpt
                .map(r -> new CsatSummaryDto(eligible, r.getSubmittedAt() != null,
                        r.getScore() != null ? r.getScore().intValue() : null, r.getComment(), r.getSubmittedAt()))
                .orElse(new CsatSummaryDto(eligible, false, null, null, null));

        return new CustomerTicketDetailDto(
                t.getId(), t.getTicketCode(), t.getSubject(), t.getDescription(), t.getStatus(),
                t.getChannelId(), t.getCategoryId(), agentName, t.getResolutionCode(),
                t.getResolvedAt(), t.getClosedAt(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion(), csat
        );
    }

    private CustomerMessageDto toCustomerMessageDto(TicketMessage m) {
        String senderName = identityDirectoryService.findUserById(m.getSenderUserId())
                .map(AppUser::getFullName)
                .orElse(m.getSenderType().name());
        return new CustomerMessageDto(
                m.getId(), m.getSenderType(), senderName, m.getDirection(),
                m.isAutoAck(), m.getBody(), m.getOccurredAt(), List.of()
        );
    }

    private StaffTicketDetailDto toStaffDetailDto(Ticket t) {
        return new StaffTicketDetailDto(
                t.getId(), t.getTicketCode(), t.getConversationId(), t.getParentTicketId(),
                t.getSubject(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getResolutionCode(), t.getCancelReason(), t.getChannelId(), t.getCategoryId(),
                t.getTeamId(), t.getAssignedAgentId(), t.getCustomerId(),
                stateMachineGuard.allowedTransitionNames(t.getStatus()),
                t.getResolvedAt(), t.getClosedAt(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion()
        );
    }

    private StaffMessageDto toStaffMessageDto(TicketMessage m) {
        String senderName = identityDirectoryService.findUserById(m.getSenderUserId())
                .map(AppUser::getFullName)
                .orElse(m.getSenderType().name());
        return new StaffMessageDto(
                m.getId(), m.getTicketId(), m.getSenderUserId(), senderName, m.getSenderType(),
                m.getDirection(), m.getVisibility(), m.isAutoAck(), m.getBody(),
                m.getDeliveryStatus(), m.getOccurredAt()
        );
    }
}
