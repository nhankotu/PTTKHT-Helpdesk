package com.helpdesk.modules.identity.repository;

import com.helpdesk.modules.identity.entity.Channel;
import com.helpdesk.modules.identity.entity.ChannelCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByCode(ChannelCode code);
}
