package com.demand.system.module.knowledge.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {

    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long uploaderId);

    PageResult<KnowledgeDocumentVO> list(Long knowledgeBaseId, int pageNum, int pageSize);

    void delete(Long knowledgeBaseId, Long documentId);

    String generateShareLink(Long knowledgeBaseId,
                             Long documentId,
                             Integer expireHours,
                             Boolean requireLogin,
                             Boolean oneTimeAccess,
                             Long creatorId);

    void syncRequirementAttachments(Long projectId, Long requirementId, List<RequirementAttachmentDTO> attachments, Long uploaderId);

    void processDocument(Long documentId);

    String resolveShareAccessUrl(String token, Long currentUserId, String accessIp, String userAgent);

    int backfillDocumentMetadata();
}
