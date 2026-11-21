package com.helpdesk.modules.ticket.service;

import com.helpdesk.common.exception.BusinessException;
import com.helpdesk.common.exception.ErrorCode;
import com.helpdesk.common.security.HelpdeskUserPrincipal;
import com.helpdesk.modules.ticket.entity.Attachment;
import com.helpdesk.modules.ticket.entity.MessageVisibility;
import com.helpdesk.modules.ticket.entity.Ticket;
import com.helpdesk.modules.ticket.entity.TicketMessage;
import com.helpdesk.modules.ticket.repository.AttachmentRepository;
import com.helpdesk.modules.ticket.repository.TicketMessageRepository;
import com.helpdesk.modules.ticket.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final long MAX_SIZE_BYTES = 26_214_400L; // 25MB khớp chk_attachment_size

    private final AttachmentRepository attachmentRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final TicketRepository ticketRepository;
    private final Path uploadRoot;
    private final Clock clock;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            TicketMessageRepository ticketMessageRepository,
            TicketRepository ticketRepository,
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            Clock clock
    ) {
        this.attachmentRepository = attachmentRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.ticketRepository = ticketRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public record AttachmentDto(
            Long id,
            Long messageId,
            String fileName,
            String mimeType,
            long sizeBytes,
            Instant createdAt
    ) {
    }

    public record DownloadedAttachment(
            String fileName,
            String mimeType,
            long sizeBytes,
            Resource resource
    ) {
    }

    @Transactional
    public AttachmentDto upload(HelpdeskUserPrincipal principal, Long messageId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.unprocessable(ErrorCode.VALIDATION_ERROR, "Tệp đính kèm không được để trống.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw BusinessException.unprocessable(ErrorCode.ATTACHMENT_TOO_LARGE, "Kích thước tệp vượt quá giới hạn 25MB.");
        }

        TicketMessage message = ticketMessageRepository.findById(messageId)
                .orElseThrow(() -> BusinessException.notFound("TicketMessage", messageId));
        Ticket ticket = ticketRepository.findById(message.getTicketId())
                .orElseThrow(() -> BusinessException.notFound("Ticket", message.getTicketId()));

        enforceAccess(principal, ticket, message);

        String originalName = file.getOriginalFilename();
        String safeFileName = (originalName == null || originalName.isBlank())
                ? "attachment.bin"
                : Paths.get(originalName).getFileName().toString();
        if (safeFileName.length() > 255) {
            safeFileName = safeFileName.substring(safeFileName.length() - 255);
        }
        String mimeType = (file.getContentType() != null && !file.getContentType().isBlank())
                ? file.getContentType()
                : "application/octet-stream";

        String storageKey = UUID.randomUUID() + "_" + safeFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path targetPath = uploadRoot.resolve(storageKey).normalize();
        if (!targetPath.startsWith(uploadRoot)) {
            throw BusinessException.unprocessable(ErrorCode.VALIDATION_ERROR, "Tên tệp không hợp lệ.");
        }

        try {
            Files.createDirectories(uploadRoot);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu tệp đính kèm lên hệ thống lưu trữ.", ex);
        }

        Attachment saved = attachmentRepository.save(new Attachment(
                message.getId(), safeFileName, mimeType, file.getSize(), storageKey, Instant.now(clock)
        ));
        return new AttachmentDto(
                saved.getId(), saved.getMessageId(), saved.getFileName(),
                saved.getMimeType(), saved.getSizeBytes(), saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DownloadedAttachment download(HelpdeskUserPrincipal principal, Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> BusinessException.notFound("Attachment", attachmentId));
        TicketMessage message = ticketMessageRepository.findById(attachment.getMessageId())
                .orElseThrow(() -> BusinessException.notFound("TicketMessage", attachment.getMessageId()));
        Ticket ticket = ticketRepository.findById(message.getTicketId())
                .orElseThrow(() -> BusinessException.notFound("Ticket", message.getTicketId()));

        enforceAccess(principal, ticket, message);

        Path targetPath = uploadRoot.resolve(attachment.getStorageKey()).normalize();
        if (!targetPath.startsWith(uploadRoot) || !Files.exists(targetPath)) {
            throw BusinessException.notFound("AttachmentFile", attachmentId);
        }
        return new DownloadedAttachment(
                attachment.getFileName(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                new FileSystemResource(targetPath)
        );
    }

    private void enforceAccess(HelpdeskUserPrincipal principal, Ticket ticket, TicketMessage message) {
        if (principal.hasRole("ADMIN") || principal.hasRole("SUPERVISOR") || principal.hasRole("AGENT")) {
            return;
        }
        // HARD INVARIANT: CUSTOMER không bao giờ được đọc/tải file thuộc tin nhắn INTERNAL hoặc ticket của người khác
        if (message.getVisibility() == MessageVisibility.INTERNAL) {
            throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Khách hàng không có quyền truy cập tệp thuộc ghi chú nội bộ.");
        }
        if (principal.customerId() == null || !principal.customerId().equals(ticket.getCustomerId())) {
            throw BusinessException.forbidden(ErrorCode.TICKET_ACCESS_DENIED, "Bạn không có quyền truy cập tệp của ticket này.");
        }
    }
}
