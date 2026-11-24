package com.helpdesk.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class OptimisticLockConflictException extends BusinessException {

    private final int currentVersion;

    public OptimisticLockConflictException(int currentVersion) {
        super(
                HttpStatus.CONFLICT,
                ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "Dữ liệu đã được cập nhật bởi một thao tác khác. Vui lòng tải lại bản mới nhất.",
                Map.of("currentVersion", currentVersion)
        );
        this.currentVersion = currentVersion;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }
}
