package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

    @PostMapping("/{documentId}/share")
    public Result<String> generateShareLink(
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "24") Integer expireHours,
            @RequestParam(defaultValue = "false") Boolean requireLogin,
            @RequestParam(defaultValue = "false") Boolean oneTimeAccess,
            HttpServletRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String token = documentService.generateShareLink(knowledgeBaseId, documentId, expireHours, requireLogin, oneTimeAccess, userId);
        String shareLink = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/api/v1/public/knowledge/shares/" + token)
                .replaceQuery(null)
                .build()
                .toUriString();
        return Result.success(shareLink);
    }
}
