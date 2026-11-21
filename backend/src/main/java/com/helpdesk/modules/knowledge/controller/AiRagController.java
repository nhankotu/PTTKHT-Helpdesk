package com.helpdesk.modules.knowledge.controller;

import com.helpdesk.common.security.SecurityUtils;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiFeedbackCreateRequest;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiFeedbackDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.AiRunDto;
import com.helpdesk.modules.knowledge.dto.KnowledgeDtos.RagSuggestionRequest;
import com.helpdesk.modules.knowledge.service.AiHelpdeskFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "6B. AI RAG Suggestions (UC07)", description = "Sinh gợi ý trả lời AI có dẫn nguồn (ai_citation), fallback an toàn và ghi nhận phản hồi Agent")
public class AiRagController {

    private final AiHelpdeskFacade aiHelpdeskFacade;

    public AiRagController(AiHelpdeskFacade aiHelpdeskFacade) {
        this.aiHelpdeskFacade = aiHelpdeskFacade;
    }

    @PostMapping("/tickets/{ticketId}/ai/suggestions")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR')")
    @Operation(summary = "Chạy AI RAG sinh gợi ý nháp có dẫn nguồn cho ticket (chỉ đọc tin PUBLIC & KB PUBLISHED)")
    public ResponseEntity<AiRunDto> generateSuggestion(
            @PathVariable Long ticketId,
            @Valid @RequestBody(required = false) RagSuggestionRequest request
    ) {
        Long userId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiHelpdeskFacade.generateRagSuggestion(ticketId, userId, request));
    }

    @GetMapping("/tickets/{ticketId}/ai/suggestions")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR', 'ADMIN')")
    @Operation(summary = "Danh sách các lần chạy AI RAG kèm trích dẫn và phản hồi của ticket")
    public ResponseEntity<List<AiRunDto>> listSuggestions(@PathVariable Long ticketId) {
        return ResponseEntity.ok(aiHelpdeskFacade.listSuggestionsForTicket(ticketId));
    }

    @PostMapping("/ai/runs/{aiRunId}/feedback")
    @PreAuthorize("hasAnyRole('AGENT', 'SUPERVISOR')")
    @Operation(summary = "Ghi nhận quyết định đánh giá của Agent đối với gợi ý AI")
    public ResponseEntity<AiFeedbackDto> submitFeedback(
            @PathVariable Long aiRunId,
            @Valid @RequestBody AiFeedbackCreateRequest request
    ) {
        Long agentId = SecurityUtils.requireCurrentPrincipal().userId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiHelpdeskFacade.submitFeedback(aiRunId, agentId, request));
    }
}
