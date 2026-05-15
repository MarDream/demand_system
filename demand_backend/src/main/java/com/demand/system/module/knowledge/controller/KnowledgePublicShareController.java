package com.demand.system.module.knowledge.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.dto.KnowledgePublicShareContextVO;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/public/knowledge/shares")
@RequiredArgsConstructor
public class KnowledgePublicShareController {

    private final KnowledgeDocumentService documentService;

    @GetMapping("/{token}")
    public void accessShare(@PathVariable String token,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        String accessUrl = documentService.resolveShareAccessUrl(token);
        response.sendRedirect(accessUrl);
    }

    @GetMapping("/{token}/context")
    public Result<KnowledgePublicShareContextVO> getShareContext(@PathVariable String token,
                                                                 HttpServletRequest request) {
        KnowledgePublicShareContextVO context = documentService.getPublicShareContext(
                token,
                SecurityUtils.getCurrentUserId(),
                extractClientIp(request),
                request.getHeader("User-Agent")
        );
        return Result.success(context);
    }

    @GetMapping("/{token}/file")
    public void previewShareFile(@PathVariable String token,
                                 @RequestParam String accessToken,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        documentService.streamSharedDocument(accessToken, false, request, response);
    }

    @GetMapping("/{token}/download")
    public void downloadShareFile(@PathVariable String token,
                                  @RequestParam String accessToken,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        documentService.streamSharedDocument(accessToken, true, request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
