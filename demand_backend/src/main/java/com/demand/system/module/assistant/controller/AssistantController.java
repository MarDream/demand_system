package com.demand.system.module.assistant.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
import com.demand.system.module.assistant.dto.AssistantRegenerateRequest;
import com.demand.system.module.assistant.dto.AssistantSessionCreateDTO;
import com.demand.system.module.assistant.dto.AssistantSessionVO;
import com.demand.system.module.assistant.dto.ExtractedQuestionVO;
import com.demand.system.module.assistant.dto.QuickQuestionCreateDTO;
import com.demand.system.module.assistant.dto.QuickQuestionVO;
import com.demand.system.module.assistant.service.AssistantService;
import com.demand.system.module.assistant.service.QuickQuestionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final QuickQuestionService quickQuestionService;

    public AssistantController(AssistantService assistantService, QuickQuestionService quickQuestionService) {
        this.assistantService = assistantService;
        this.quickQuestionService = quickQuestionService;
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

    @PostMapping(value = "/sessions/{sessionId}/messages/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter regenerateMessage(@PathVariable Long sessionId, @Valid @RequestBody AssistantRegenerateRequest request) {
        return assistantService.regenerateMessage(sessionId, request);
    }

    // ==================== 快捷提问 API ====================

    /**
     * 前台获取快捷提问列表（人工优先 + AI 补齐，最多 3 条）
     * 轮询间隔：每 10s 查询一次
     */
    @GetMapping("/quick-questions")
    @PreAuthorize("isAuthenticated()")
    public Result<List<QuickQuestionVO>> getQuickQuestions(@RequestParam(required = false, defaultValue = "") String pageRoute) {
        return Result.success(quickQuestionService.getForFrontend(pageRoute));
    }

    /**
     * 记录快捷提问点击（增加 hit_count）
     */
    @PostMapping("/quick-questions/{id}/click")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> recordClick(@PathVariable Long id) {
        quickQuestionService.recordClick(id);
        return Result.success();
    }

    // ==================== 后台管理 API（ADMIN） ====================

    /**
     * 后台：查询全部快捷问题
     */
    @GetMapping("/admin/quick-questions")
    @PreAuthorize("hasAuthority('admin')")
    public Result<List<QuickQuestionVO>> listAll(
            @RequestParam(required = false) String pageRoute,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        return Result.success(quickQuestionService.listAll(pageRoute, status, category));
    }

    /**
     * 后台：获取 AI 自动提炼的高频问题
     */
    @GetMapping("/admin/quick-questions/extracted")
    @PreAuthorize("hasAuthority('admin')")
    public Result<List<ExtractedQuestionVO>> getExtracted(
            @RequestParam(required = false, defaultValue = "30") int windowDays,
            @RequestParam(required = false, defaultValue = "5") int minFrequency) {
        return Result.success(quickQuestionService.getExtracted(windowDays, minFrequency));
    }

    /**
     * 后台：创建快捷问题
     */
    @PostMapping("/admin/quick-questions")
    @PreAuthorize("hasAuthority('admin')")
    public Result<QuickQuestionVO> create(@Valid @RequestBody QuickQuestionCreateDTO dto) {
        return Result.success(quickQuestionService.create(dto));
    }

    /**
     * 后台：更新快捷问题
     */
    @PutMapping("/admin/quick-questions/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<QuickQuestionVO> update(@PathVariable Long id, @Valid @RequestBody QuickQuestionCreateDTO dto) {
        return Result.success(quickQuestionService.update(id, dto));
    }

    /**
     * 后台：删除快捷问题
     */
    @DeleteMapping("/admin/quick-questions/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> delete(@PathVariable Long id) {
        quickQuestionService.delete(id);
        return Result.success();
    }

    /**
     * 后台：启用/停用
     */
    @PutMapping("/admin/quick-questions/{id}/status")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam String status) {
        quickQuestionService.toggleStatus(id, status);
        return Result.success();
    }

    /**
     * 后台：采纳 AI 提炼建议 → 转入人工维护（从埋点日志聚合而来，无 quick_questions.id）
     */
    @PostMapping("/admin/quick-questions/adopt")
    @PreAuthorize("hasAuthority('admin')")
    public Result<QuickQuestionVO> adoptAiSuggestion(@RequestBody Map<String, String> body) {
        String questionText = body.get("questionText");
        String pageRoute = body.get("pageRoute");
        String questionHash = body.get("questionHash");
        return Result.success(quickQuestionService.adoptAiSuggestion(questionText, pageRoute, questionHash));
    }
}
