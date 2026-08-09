package com.demand.system.module.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.result.Result;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.ImageUnderstandingService;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/requirement-bodies")
public class RequirementBodyIndexController {

    private static final String SOURCE_TYPE = "requirement_body";

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final RequirementMapper requirementMapper;
    private final ImageUnderstandingService imageUnderstandingService;

    public RequirementBodyIndexController(KnowledgeDocumentService knowledgeDocumentService,
                                          KnowledgeDocumentMapper knowledgeDocumentMapper,
                                          KnowledgeChunkMapper knowledgeChunkMapper,
                                          RequirementMapper requirementMapper,
                                          ImageUnderstandingService imageUnderstandingService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.requirementMapper = requirementMapper;
        this.imageUnderstandingService = imageUnderstandingService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> overview() {
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getSourceType, SOURCE_TYPE)
                        .orderByDesc(KnowledgeDocument::getId));
        Map<Long, KnowledgeDocument> latestByRequirement = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            if (document.getSourceId() != null) {
                latestByRequirement.putIfAbsent(document.getSourceId(), document);
            }
        }

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (KnowledgeDocument document : latestByRequirement.values()) {
            String status = document.getStatus() == null || document.getStatus().isBlank()
                    ? "unknown" : document.getStatus();
            statusCounts.merge(status, 1L, Long::sum);
        }
        long totalRequirements = Optional.ofNullable(requirementMapper.selectCount(
                new LambdaQueryWrapper<Requirement>())).orElse(0L);
        long indexedRequirements = latestByRequirement.values().stream()
                .filter(document -> "indexed".equalsIgnoreCase(document.getStatus()))
                .count();
        Set<Long> requirementBodyDocumentIds = latestByRequirement.values().stream()
                .filter(document -> "indexed".equalsIgnoreCase(document.getStatus()))
                .map(KnowledgeDocument::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        long imageChunkCount = requirementBodyDocumentIds.isEmpty() ? 0L
                : Optional.ofNullable(knowledgeChunkMapper.selectCount(
                        new LambdaQueryWrapper<KnowledgeChunk>()
                                .in(KnowledgeChunk::getDocumentId, requirementBodyDocumentIds)
                                .in(KnowledgeChunk::getSourceContentType, "image_ocr", "image_caption")))
                .orElse(0L);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalRequirements", totalRequirements);
        body.put("indexedRequirements", indexedRequirements);
        body.put("notIndexedRequirements", Math.max(0, totalRequirements - indexedRequirements));
        body.put("statusCounts", statusCounts);
        body.put("imageChunkCount", imageChunkCount);
        body.put("imageUnderstandingEnabled", imageUnderstandingService.enabled());
        body.put("imageUnderstandingReason", imageUnderstandingService.enabled()
                ? null : imageUnderstandingService.unavailableReason());
        return Result.success(body);
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> backfill() {
        int submitted = knowledgeDocumentService.backfillRequirementBodies();
        return Result.success(Map.of("submitted", submitted));
    }

    @PostMapping("/{requirementId}/rebuild")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> rebuild(@PathVariable Long requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            return Result.success(Map.of("submitted", false, "requirementId", requirementId, "message", "工单不存在或已删除"));
        }
        knowledgeDocumentService.syncRequirementBody(
                requirement.getProjectId(), requirement.getId(), requirement.getRequirementNo(),
                requirement.getTitle(), requirement.getDescription(), requirement.getCreatorId());
        return Result.success(Map.of("submitted", true, "requirementId", requirementId));
    }

    @GetMapping("/{requirementId}/status")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> status(@PathVariable Long requirementId) {
        KnowledgeDocument document = latestDocument(requirementId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirementId", requirementId);
        body.put("documentId", document == null ? null : document.getId());
        body.put("status", document == null ? "not_indexed" : document.getStatus());
        body.put("chunkCount", document == null ? 0 : Optional.ofNullable(document.getChunkCount()).orElse(0));
        body.put("errorMessage", document == null ? null : document.getErrorMessage());
        body.put("updatedAt", document == null ? null : document.getUpdatedAt());
        body.put("imageUnderstandingEnabled", imageUnderstandingService.enabled());
        body.put("imageUnderstandingReason", imageUnderstandingService.enabled() ? null : imageUnderstandingService.unavailableReason());
        return Result.success(body);
    }

    @PostMapping("/retry-failed")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> retryFailed() {
        List<KnowledgeDocument> failed = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getSourceType, SOURCE_TYPE)
                .eq(KnowledgeDocument::getStatus, "failed")
                .orderByAsc(KnowledgeDocument::getId));
        int submitted = 0;
        int skipped = 0;
        Set<Long> submittedRequirementIds = new HashSet<>();
        for (KnowledgeDocument document : failed) {
            Long requirementId = document.getSourceId();
            if (requirementId == null || !submittedRequirementIds.add(requirementId)) {
                continue;
            }
            Requirement requirement = requirementMapper.selectById(requirementId);
            if (requirement == null) {
                skipped++;
                continue;
            }
            knowledgeDocumentService.syncRequirementBody(
                    requirement.getProjectId(), requirement.getId(), requirement.getRequirementNo(),
                    requirement.getTitle(), requirement.getDescription(), requirement.getCreatorId());
            submitted++;
        }
        return Result.success(Map.of("submitted", submitted, "skipped", skipped, "total", failed.size()));
    }

    @PostMapping("/rebuild-batch")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> rebuildBatch(@RequestBody(required = false) Map<String, Object> request) {
        List<Long> ids = extractIds(request == null ? null : request.get("requirementIds"));
        List<Requirement> requirements = ids.isEmpty()
                ? requirementMapper.selectList(new LambdaQueryWrapper<Requirement>().orderByAsc(Requirement::getId))
                : requirementMapper.selectBatchIds(ids);
        int submitted = 0;
        int skipped = 0;
        for (Requirement requirement : requirements) {
            if (requirement == null || requirement.getId() == null) {
                skipped++;
                continue;
            }
            knowledgeDocumentService.syncRequirementBody(
                    requirement.getProjectId(), requirement.getId(), requirement.getRequirementNo(),
                    requirement.getTitle(), requirement.getDescription(), requirement.getCreatorId());
            submitted++;
        }
        return Result.success(Map.of("submitted", submitted, "skipped", skipped, "requested", ids.size(), "total", requirements.size()));
    }

    private KnowledgeDocument latestDocument(Long requirementId) {
        return knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getSourceType, SOURCE_TYPE)
                .eq(KnowledgeDocument::getSourceId, requirementId)
                .orderByDesc(KnowledgeDocument::getId)
                .last("LIMIT 1"));
    }

    private List<Long> extractIds(Object value) {
        if (!(value instanceof Collection<?> collection)) return List.of();
        List<Long> ids = new ArrayList<>();
        for (Object item : collection) {
            try {
                if (item != null) ids.add(Long.valueOf(String.valueOf(item)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed IDs and continue processing the batch.
            }
        }
        return ids.stream().distinct().toList();
    }
}
