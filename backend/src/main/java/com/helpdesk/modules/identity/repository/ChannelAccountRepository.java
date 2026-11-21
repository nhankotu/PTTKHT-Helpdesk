package com.helpdesk.modules.identity.repository;

import com.helpdesk.modules.identity.entity.ChannelAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelAccountRepository extends JpaRepository<ChannelAccount, Long> {

    @EntityGraph(attributePaths = "channel")
    Optional<ChannelAccount> findByAccountCode(String accountCode);
}
