package com.demand.system.module.assistant.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
import com.demand.system.module.assistant.dto.AssistantSessionCreateDTO;
import com.demand.system.module.assistant.dto.AssistantSessionVO;
import com.demand.system.module.assistant.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AssistantSessionVO>> listSessions() {
        return Result.success(assistantService.listSessions());
    }

    @PostMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public Result<AssistantSessionVO> createSession(@Valid @RequestBody(required = false) AssistantSessionCreateDTO createDTO) {
        return Result.success(assistantService.createSession(createDTO));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AssistantMessageVO>> listMessages(@PathVariable Long sessionId) {
        return Result.success(assistantService.listMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        assistantService.deleteSession(sessionId);
        return Result.success();
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamMessage(@PathVariable Long sessionId, @Valid @RequestBody AssistantChatRequest request) {
        return assistantService.streamMessage(sessionId, request);
    }
}
