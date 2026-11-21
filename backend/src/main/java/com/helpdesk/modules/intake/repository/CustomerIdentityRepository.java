package com.helpdesk.modules.intake.repository;

import com.helpdesk.modules.intake.entity.CustomerIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerIdentityRepository extends JpaRepository<CustomerIdentity, Long> {

    Optional<CustomerIdentity> findByChannelAccountIdAndExternalUserId(Long channelAccountId, String externalUserId);
}
