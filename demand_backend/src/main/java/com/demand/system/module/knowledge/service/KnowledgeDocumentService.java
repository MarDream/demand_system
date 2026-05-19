package com.demand.system.module.knowledge.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.knowledge.dto.KnowledgePublicShareContextVO;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {

    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long uploaderId);

    PageResult<KnowledgeDocumentVO> list(Long knowledgeBaseId,
                                         int pageNum,
                                         int pageSize,
                                         String fileName,
                                         String status,
                                         String createdAtStart,
                                         String createdAtEnd);

    void delete(Long knowledgeBaseId, Long documentId);

    String generateShareLink(Long knowledgeBaseId,
                             Long documentId,
                             Integer expireHours,
                             Boolean requireLogin,
                             Boolean oneTimeAccess,
                             Long creatorId);

    void syncRequirementAttachments(Long projectId, Long requirementId, List<RequirementAttachmentDTO> attachments, Long uploaderId);

    void processDocument(Long documentId);

    String resolveShareAccessUrl(String token);

    KnowledgePublicShareContextVO getPublicShareContext(String token, Long currentUserId, String accessIp, String userAgent);

    void streamSharedDocument(String shareAccessToken, boolean download, HttpServletRequest request, HttpServletResponse response);

    int backfillDocumentMetadata();

    int retryDocuments(Long knowledgeBaseId, List<Long> documentIds);

    int batchDelete(Long knowledgeBaseId, List<Long> documentIds);

    String getPreviewUrl(Long knowledgeBaseId, Long documentId);

    void downloadDocument(Long knowledgeBaseId, Long documentId, HttpServletResponse response);
}
