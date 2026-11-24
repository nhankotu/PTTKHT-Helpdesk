package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State Machine Guard cho 7 trạng thái Ticket hợp nhất (UC03).
 * Mọi thay đổi trạng thái bắt buộc đi qua lớp này.
 */
@Component
public class TicketStateMachineGuard {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TicketStatus.NEW, Set.of(
                TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.ASSIGNED, Set.of(
                TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS, Set.of(
                TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.WAITING_CUSTOMER, Set.of(
                TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CANCELLED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.RESOLVED, Set.of(
                TicketStatus.IN_PROGRESS, TicketStatus.CLOSED
        ));
        ALLOWED_TRANSITIONS.put(TicketStatus.CLOSED, Set.of());
        ALLOWED_TRANSITIONS.put(TicketStatus.CANCELLED, Set.of());
    }

    public List<String> allowedTransitionNames(TicketStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    public void validateTransition(
            TicketStatus current,
            TicketStatus target,
            String resolutionCode,
            String cancelReason
    ) {
        if (current == target) {
            return;
        }
        Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Không thể chuyển trạng thái từ " + current + " sang " + target + ".",
                    Map.of(
                            "currentStatus", current.name(),
                            "targetStatus", target.name(),
                            "allowedTransitions", allowedTransitionNames(current)
                    )
            );
        }

        if (target == TicketStatus.RESOLVED && (resolutionCode == null || resolutionCode.isBlank())) {
            throw BusinessException.unprocessable(
                    ErrorCode.RESOLUTION_CODE_REQUIRED,
                    "Bắt buộc cung cấp resolutionCode khi chuyển ticket sang trạng thái RESOLVED."
            );
        }

        if (target == TicketStatus.CANCELLED) {
            if (cancelReason == null || cancelReason.trim().length() < 10) {
                throw BusinessException.unprocessable(
                        ErrorCode.CANCEL_REASON_TOO_SHORT,
                        "Lý do hủy ticket (cancelReason) phải có tối thiểu 10 ký tự."
                );
            }
        }
    }
}
