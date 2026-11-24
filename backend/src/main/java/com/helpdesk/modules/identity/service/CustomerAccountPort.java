package com.helpdesk.modules.identity.service;

import java.util.Optional;

/**
 * Port giải耦 (Dependency Inversion) giữa module identity và module intake (bảng customer).
 * Khi module intake được triển khai sẽ cung cấp Bean implement port này.
 */
public interface CustomerAccountPort {

    Optional<Long> findCustomerIdByUserId(Long userId);

    Long createVerifiedCustomerForUser(Long userId, String fullName, String email, String phone);
}
