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

    /**
     * 同步需求附件到知识库（包含需求上下文信息）
     *
     * @param projectId 项目ID
     * @param requirementId 需求ID
     * @param requirementCode 需求编号
     * @param requirementTitle 需求标题
     * @param attachments 附件列表
     * @param uploaderId 上传人ID
     */
    void syncRequirementAttachmentsWithContext(Long projectId, Long requirementId, String requirementCode,
                                               String requirementTitle, List<RequirementAttachmentDTO> attachments, Long uploaderId);

    /**
     * 获取文档的需求引用列表
     *
     * @param documentId 文档ID
     * @return 需求引用列表
     */
    List<com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef> getDocumentRequirementRefs(Long documentId);

    void processDocument(Long documentId);

    String resolveShareAccessUrl(String token);

    KnowledgePublicShareContextVO getPublicShareContext(String token, Long currentUserId, String accessIp, String userAgent);

    void streamSharedDocument(String shareAccessToken, boolean download, HttpServletRequest request, HttpServletResponse response);

    int backfillDocumentMetadata();

    int retryDocuments(Long knowledgeBaseId, List<Long> documentIds);

    int batchDelete(Long knowledgeBaseId, List<Long> documentIds);

    /**
     * 跳过文档的索引（仅保留文件存储）。
     *
     * <p>适用于"持续索引中"卡死、已经放弃向量化的大文件场景。操作会清空已有 chunks
     * 和 Milvus 向量，并把 status 切到 stored。预览和下载功能不受影响。</p>
     */
    void skipIndexing(Long knowledgeBaseId, Long documentId);

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
