package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge/bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService documentService;

    @PostMapping("/upload")
    public Result<KnowledgeDocumentVO> upload(
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        KnowledgeDocumentVO vo = documentService.upload(knowledgeBaseId, file, userId);
        return Result.success(vo);
    }

    @GetMapping
    public Result<PageResult<KnowledgeDocumentVO>> list(
            @PathVariable Long knowledgeBaseId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<KnowledgeDocumentVO> result = documentService.list(knowledgeBaseId, pageNum, pageSize);
        return Result.success(result);
    }

    @DeleteMapping("/{documentId}")
    public Result<Void> delete(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId) {
        documentService.delete(knowledgeBaseId, documentId);
        return Result.success();
    }
}
