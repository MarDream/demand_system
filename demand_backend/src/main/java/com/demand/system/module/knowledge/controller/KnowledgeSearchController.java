package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSearchController {
    private final KnowledgeSearchService searchService;

    public KnowledgeSearchController(KnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public Result<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        KnowledgeSearchResponse response = searchService.search(request);
        return Result.success(response);
    }

    @PostMapping(value = "/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamSearch(@Valid @RequestBody KnowledgeSearchRequest request) {
        return searchService.streamSearch(request);
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("module", "knowledge-rag");
        stats.put("status", "active");
        return Result.success(stats);
    }
}
