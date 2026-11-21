package com.helpdesk.common.security;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<HelpdeskUserPrincipal> getCurrentPrincipalOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HelpdeskUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static HelpdeskUserPrincipal requireCurrentPrincipal() {
        return getCurrentPrincipalOptional()
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED,
                        "Yêu cầu đăng nhập để thực hiện thao tác này."
                ));
    }
}
