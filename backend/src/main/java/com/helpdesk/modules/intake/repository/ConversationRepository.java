package com.helpdesk.modules.intake.repository;

import com.helpdesk.modules.intake.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByChannelAccountIdAndExternalThreadId(Long channelAccountId, String externalThreadId);
}
