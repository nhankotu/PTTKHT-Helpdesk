package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.modules.ticket.entity.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketStateMachineGuardTest {

    private final TicketStateMachineGuard guard = new TicketStateMachineGuard();

    @Test
    @DisplayName("Cho phép chuỗi chuyển trạng thái chuẩn NEW -> ASSIGNED -> IN_PROGRESS -> WAITING_CUSTOMER -> RESOLVED -> CLOSED")
    void allowsValidLifecycleTransitions() {
        assertThatCode(() -> {
            guard.validateTransition(TicketStatus.NEW, TicketStatus.ASSIGNED, null, null);
            guard.validateTransition(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, null, null);
            guard.validateTransition(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER, null, null);
            guard.validateTransition(TicketStatus.WAITING_CUSTOMER, TicketStatus.IN_PROGRESS, null, null);
            guard.validateTransition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, "FIXED", null);
            guard.validateTransition(TicketStatus.RESOLVED, TicketStatus.CLOSED, null, null);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Từ chối chuyển từ CLOSED sang IN_PROGRESS với mã lỗi INVALID_STATE_TRANSITION")
    void rejectsClosedToInProgress() {
        assertThatThrownBy(() -> guard.validateTransition(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("Bắt buộc resolutionCode khi chuyển sang RESOLVED")
    void requiresResolutionCodeForResolved() {
        assertThatThrownBy(() -> guard.validateTransition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, "   ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOLUTION_CODE_REQUIRED);
    }

    @Test
    @DisplayName("Bắt buộc cancelReason tối thiểu 10 ký tự khi hủy ticket")
    void requiresCancelReasonAtLeast10Chars() {
        assertThatThrownBy(() -> guard.validateTransition(TicketStatus.NEW, TicketStatus.CANCELLED, null, "spam"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANCEL_REASON_TOO_SHORT);

        assertThatCode(() -> guard.validateTransition(TicketStatus.NEW, TicketStatus.CANCELLED, null, "Yêu cầu spam trùng lặp nhiều lần"))
                .doesNotThrowAnyException();
    }
}
