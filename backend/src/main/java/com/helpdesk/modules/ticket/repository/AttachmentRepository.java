package com.helpdesk.modules.ticket.repository;

import com.helpdesk.modules.ticket.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByMessageIdOrderByCreatedAtAsc(Long messageId);
}
