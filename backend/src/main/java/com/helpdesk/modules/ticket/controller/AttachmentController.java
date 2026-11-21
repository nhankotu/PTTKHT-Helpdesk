package com.helpdesk.modules.ticket.controller;

import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.ticket.service.AttachmentService;
import com.helpdesk.modules.ticket.service.AttachmentService.AttachmentDto;
import com.helpdesk.modules.ticket.service.AttachmentService.DownloadedAttachment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "2B. Attachments (UC01-UC03)", description = "Upload và tải tệp đính kèm (<= 25MB, cách ly tuyệt đối file thuộc ghi chú INTERNAL)")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload tệp đính kèm cho một tin nhắn ticket (<= 25MB)")
    public ResponseEntity<AttachmentDto> upload(
            @RequestParam("messageId") Long messageId,
            @RequestPart("file") MultipartFile file
    ) {
        AttachmentDto dto = attachmentService.upload(SecurityUtils.requireCurrentPrincipal(), messageId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{attachmentId}/download")
    @Operation(summary = "Tải tệp đính kèm (chặn CUSTOMER tải file thuộc tin nhắn INTERNAL)")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        DownloadedAttachment downloaded = attachmentService.download(SecurityUtils.requireCurrentPrincipal(), attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloaded.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(downloaded.mimeType()))
                .contentLength(downloaded.sizeBytes())
                .body(downloaded.resource());
    }
}
