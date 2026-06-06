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
                                         String createdAtEnd,
                                         String projectName,
                                         Long requirementId);

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

    /**
     * 获取文档预览地址（默认有效期）。
     *
     * @return MinIO 预签名 URL
     */
    String getPreviewUrl(Long knowledgeBaseId, Long documentId);

    /**
     * 获取文档预览地址（自定义有效期）。
     *
     * <p>用于预览链路按需签发：每次打开预览时由后端临时签发一个有效期更长的 MinIO URL，
     * 避免 kkFileView 异步转码 + 浏览器渲染过程中 URL 过期。</p>
     *
     * @param expiryHours 预签名 URL 有效期（小时），推荐 24
     * @return MinIO 预签名 URL
     */
    String getPreviewUrl(Long knowledgeBaseId, Long documentId, int expiryHours);

    void downloadDocument(Long knowledgeBaseId, Long documentId, HttpServletResponse response);

    void batchDownloadDocuments(Long knowledgeBaseId, List<Long> documentIds, HttpServletResponse response);
}
