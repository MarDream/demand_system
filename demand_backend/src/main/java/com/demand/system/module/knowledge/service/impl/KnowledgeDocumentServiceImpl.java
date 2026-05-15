package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import com.demand.system.module.knowledge.dto.KnowledgePublicShareContextVO;
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.entity.KnowledgeDocumentShare;
import com.demand.system.module.knowledge.entity.KnowledgeDocumentShareLog;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentShareLogMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentShareMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.file.storage.MinioStorageService;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SysUserMapper sysUserMapper;
    private final RequirementMapper requirementMapper;
    private final MinioStorageService minioStorageService;
    private final EmbeddingService embeddingService;
    private final KnowledgeConfig knowledgeConfig;
    private final MilvusVectorStore milvusVectorStore;
    private final RabbitTemplate rabbitTemplate;
    private final KnowledgeDocumentShareMapper shareMapper;
    private final KnowledgeDocumentShareLogMapper shareLogMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long uploaderId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }

        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        String minioKey = "knowledge/" + knowledgeBaseId + "/" + UUID.randomUUID() + "/" + originalName;

        try {
            minioStorageService.upload(file.getInputStream(), minioKey, file.getContentType());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件上传失败: " + e.getMessage());
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setProjectId(kb.getProjectId());
        doc.setFileName(originalName);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus("pending");
        doc.setMinioKey(minioKey);
        doc.setSourceType("knowledge_base");
        doc.setSourceId(knowledgeBaseId);
        doc.setUploaderId(uploaderId);
        documentMapper.insert(doc);
        enqueueDocumentProcessing(doc.getId());

        return toVO(doc);
    }

    @Override
    public PageResult<KnowledgeDocumentVO> list(Long knowledgeBaseId, int pageNum, int pageSize) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.orderByDesc(KnowledgeDocument::getCreatedAt);
        Page<KnowledgeDocument> page = documentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<KnowledgeDocumentVO> voList = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public void delete(Long knowledgeBaseId, Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }

        // 删除Milvus中的向量
        try {
            milvusVectorStore.deleteByDocumentId(String.valueOf(documentId));
        } catch (Exception e) {
            log.warn("Milvus向量删除失败: documentId={}", documentId, e);
        }

        // 删除分块记录
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));

        // 删除MinIO文件
        if (doc.getMinioKey() != null) {
            try {
                minioStorageService.delete(doc.getMinioKey());
            } catch (Exception e) {
                log.warn("MinIO文件删除失败: {}", doc.getMinioKey(), e);
            }
        }

        // 删除文档记录
        documentMapper.deleteById(documentId);

        // 更新知识库计数
        updateKnowledgeBaseCount(knowledgeBaseId);
    }

    @Override
    public String generateShareLink(Long knowledgeBaseId,
                                    Long documentId,
                                    Integer expireHours,
                                    Boolean requireLogin,
                                    Boolean oneTimeAccess,
                                    Long creatorId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        int hours = expireHours == null || expireHours <= 0 ? 24 : Math.min(expireHours, 168);
        KnowledgeDocumentShare share = new KnowledgeDocumentShare();
        share.setToken(UUID.randomUUID().toString().replace("-", ""));
        share.setKnowledgeBaseId(knowledgeBaseId);
        share.setDocumentId(documentId);
        share.setCreatorId(creatorId);
        share.setRequireLogin(Boolean.TRUE.equals(requireLogin) ? 1 : 0);
        share.setOneTimeAccess(Boolean.TRUE.equals(oneTimeAccess) ? 1 : 0);
        share.setUsedCount(0);
        share.setStatus("active");
        share.setExpireAt(LocalDateTime.now().plusHours(hours));
        shareMapper.insert(share);
        return share.getToken();
    }

    @Override
    @Transactional
    public void syncRequirementAttachments(Long projectId, Long requirementId, List<RequirementAttachmentDTO> attachments, Long uploaderId) {
        if (projectId == null || requirementId == null || attachments == null || attachments.isEmpty()) {
            return;
        }

        Long knowledgeBaseId = ensureProjectAttachmentKnowledgeBase(projectId, uploaderId);
        for (RequirementAttachmentDTO attachment : attachments) {
            if (attachment == null || attachment.getObjectName() == null || attachment.getObjectName().isBlank()) {
                continue;
            }
            KnowledgeDocument exists = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                    .eq(KnowledgeDocument::getMinioKey, attachment.getObjectName())
                    .last("LIMIT 1"));
            if (exists != null) {
                if (exists.getRequirementId() == null || !requirementId.equals(exists.getRequirementId())) {
                    exists.setRequirementId(requirementId);
                    documentMapper.updateById(exists);
                }
                continue;
            }

            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setKnowledgeBaseId(knowledgeBaseId);
            doc.setFileName(attachment.getName());
            doc.setFileType(extractFileType(attachment.getName()));
            doc.setFileSize(attachment.getSize());
            doc.setChunkCount(0);
            doc.setStatus("pending");
            doc.setMinioKey(attachment.getObjectName());
            doc.setProjectId(projectId);
            doc.setRequirementId(requirementId);
            doc.setSourceType("requirement");
            doc.setSourceId(requirementId);
            doc.setUploaderId(uploaderId);
            documentMapper.insert(doc);
            enqueueDocumentProcessing(doc.getId());
        }
    }

    @Override
    @Transactional
    public String resolveShareAccessUrl(String token) {
        return "/public/share/" + token;
    }

    @Override
    @Transactional
    public KnowledgePublicShareContextVO getPublicShareContext(String token, Long currentUserId, String accessIp, String userAgent) {
        ShareValidationResult result = validateShareForAccess(token, currentUserId, accessIp, userAgent, true);
        String accessToken = generateShareAccessToken(result.share(), result.document(), currentUserId);
        return KnowledgePublicShareContextVO.builder()
                .shareToken(result.share().getToken())
                .accessToken(accessToken)
                .knowledgeBaseId(result.share().getKnowledgeBaseId())
                .documentId(result.share().getDocumentId())
                .fileName(result.document().getFileName())
                .fileType(result.document().getFileType())
                .expireAt(result.share().getExpireAt())
                .requireLogin(asBoolean(result.share().getRequireLogin()))
                .oneTimeAccess(asBoolean(result.share().getOneTimeAccess()))
                .build();
    }

    @Override
    public void streamSharedDocument(String shareAccessToken, boolean download, HttpServletRequest request, HttpServletResponse response) {
        ShareAccessClaims claims = parseShareAccessToken(shareAccessToken);
        KnowledgeDocument document = documentMapper.selectById(claims.documentId());
        if (document == null || !Objects.equals(document.getKnowledgeBaseId(), claims.knowledgeBaseId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享文档不存在");
        }

        String rangeHeader = request.getHeader("Range");
        boolean headOnly = "HEAD".equalsIgnoreCase(request.getMethod());

        try {
            long fileSize = resolveFileSize(document);
            String encodedName = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");

            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader(
                    "Content-Disposition",
                    (download ? "attachment" : "inline") + "; filename*=UTF-8''" + encodedName
            );
            response.setContentType(getContentType(document.getFileType()));

            ByteRange range = parseRange(rangeHeader, fileSize);
            if (rangeHeader != null && range == null) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            if (range != null) {
                long length = range.end() - range.start() + 1;
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + range.start() + "-" + range.end() + "/" + fileSize);
                response.setContentLengthLong(length);
                if (headOnly) {
                    return;
                }
                try (InputStream is = minioStorageService.download(document.getMinioKey(), range.start(), length)) {
                    is.transferTo(response.getOutputStream());
                }
            } else {
                response.setContentLengthLong(fileSize);
                if (headOnly) {
                    return;
                }
                try (InputStream is = minioStorageService.download(document.getMinioKey())) {
                    is.transferTo(response.getOutputStream());
                }
            }

            response.getOutputStream().flush();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分享文件访问失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public int backfillDocumentMetadata() {
        List<KnowledgeDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDeletedAt, 0)
                .orderByAsc(KnowledgeDocument::getId));
        if (documents.isEmpty()) {
            return 0;
        }

        Map<Long, KnowledgeBase> knowledgeBaseMap = knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getDeletedAt, 0))
                .stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, kb -> kb, (left, right) -> left));

        List<Requirement> requirements = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getDeletedAt, 0)
                .isNotNull(Requirement::getAttachments)
                .orderByDesc(Requirement::getUpdatedAt));
        Map<String, Requirement> requirementByObjectName = new HashMap<>();
        Map<String, Requirement> requirementByFileName = new HashMap<>();
        for (Requirement requirement : requirements) {
            if (requirement.getAttachments() == null) {
                continue;
            }
            for (RequirementAttachmentDTO attachment : requirement.getAttachments()) {
                if (attachment == null) {
                    continue;
                }
                if (attachment.getObjectName() != null && !attachment.getObjectName().isBlank()) {
                    requirementByObjectName.putIfAbsent(attachment.getObjectName(), requirement);
                }
                if (attachment.getName() != null && !attachment.getName().isBlank()) {
                    requirementByFileName.putIfAbsent(attachment.getName().toLowerCase(), requirement);
                }
            }
        }

        int updatedCount = 0;
        for (KnowledgeDocument doc : documents) {
            boolean changed = false;
            KnowledgeBase kb = knowledgeBaseMap.get(doc.getKnowledgeBaseId());
            if (doc.getProjectId() == null && kb != null) {
                doc.setProjectId(kb.getProjectId());
                changed = true;
            }

            Requirement matchedRequirement = null;
            if (doc.getRequirementId() != null) {
                matchedRequirement = requirementMapper.selectById(doc.getRequirementId());
            }
            if (matchedRequirement == null && doc.getMinioKey() != null) {
                matchedRequirement = requirementByObjectName.get(doc.getMinioKey());
            }
            if (matchedRequirement == null && doc.getFileName() != null) {
                matchedRequirement = requirementByFileName.get(doc.getFileName().toLowerCase());
            }
            if (matchedRequirement != null) {
                if (!Objects.equals(doc.getRequirementId(), matchedRequirement.getId())) {
                    doc.setRequirementId(matchedRequirement.getId());
                    changed = true;
                }
                if (!Objects.equals(doc.getProjectId(), matchedRequirement.getProjectId())) {
                    doc.setProjectId(matchedRequirement.getProjectId());
                    changed = true;
                }
                if (!"requirement".equals(doc.getSourceType())) {
                    doc.setSourceType("requirement");
                    changed = true;
                }
                if (!Objects.equals(doc.getSourceId(), matchedRequirement.getId())) {
                    doc.setSourceId(matchedRequirement.getId());
                    changed = true;
                }
            } else {
                if (!"knowledge_base".equals(doc.getSourceType())) {
                    doc.setSourceType("knowledge_base");
                    changed = true;
                }
                if (!Objects.equals(doc.getSourceId(), doc.getKnowledgeBaseId())) {
                    doc.setSourceId(doc.getKnowledgeBaseId());
                    changed = true;
                }
            }

            if (changed) {
                documentMapper.updateById(doc);
                updatedCount++;
            }
        }
        return updatedCount;
    }

    @Override
    @Transactional
    private static final Set<String> SKIP_EMBEDDING_TYPES = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg",
            "zip", "rar", "7z", "tar", "gz", "bz2"
    );

    @Override
    public void processDocument(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }

        // 图片和压缩包无需文本解析和向量化，直接标记完成
        if (SKIP_EMBEDDING_TYPES.contains(doc.getFileType() != null ? doc.getFileType().toLowerCase() : "")) {
            doc.setChunkCount(0);
            doc.setStatus("indexed");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        doc.setStatus("parsed");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        // 读取文件内容
        String content;
        try {
            content = readDocumentContent(doc);
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage("文件读取失败: " + e.getMessage());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            return;
        }

        if (content == null || content.isBlank()) {
            doc.setChunkCount(0);
            doc.setStatus("indexed");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        // 分块
        List<String> chunks = splitContent(content);
        chunks = chunks.stream()
                .map(KnowledgeDocumentServiceImpl::sanitizeText)
                .filter(c -> !c.isBlank())
                .toList();
        if (chunks.isEmpty()) {
            doc.setChunkCount(0);
            doc.setStatus("indexed");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        doc.setStatus("indexing");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        // 生成向量并存储到MySQL和Milvus
        List<MilvusVectorStore.VectorDocument> milvusDocs = new ArrayList<>();
        try {
            List<float[]> vectors = embedInBatches(chunks);

            int validChunks = 0;
            for (int i = 0; i < chunks.size(); i++) {
                float[] vector = vectors.get(i);
                if (vector == null || vector.length == 0) {
                    continue;
                }

                String vectorId = UUID.randomUUID().toString();
                validChunks++;

                // MySQL元数据
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(doc.getId());
                chunk.setKnowledgeBaseId(doc.getKnowledgeBaseId());
                chunk.setChunkIndex(validChunks - 1);
                chunk.setContent(chunks.get(i));
                chunk.setCharCount(chunks.get(i).length());
                chunk.setVectorId(vectorId);
                chunkMapper.insert(chunk);

                // Milvus向量
                milvusDocs.add(new MilvusVectorStore.VectorDocument(
                        vectorId, vector,
                        doc.getKnowledgeBaseId(), doc.getId(),
                        validChunks - 1, chunks.get(i), null, null,
                        doc.getFileName(), doc.getFileType()
                ));
            }

            // 批量写入Milvus
            if (!milvusDocs.isEmpty()) {
                milvusVectorStore.insertVectors(milvusDocs);
            }

        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage("向量化失败: " + e.getMessage());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            return;
        }

        doc.setChunkCount(milvusDocs.size());
        doc.setStatus("indexed");
        doc.setErrorMessage(null);
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        // 更新知识库计数
        updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
    }

    private void enqueueDocumentProcessing(Long docId) {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendDocumentProcessingMessage(docId);
                    }
                });
            } else {
                sendDocumentProcessingMessage(docId);
            }
        } catch (Exception e) {
            log.warn("消息队列发送失败，同步处理: docId={}", docId, e);
            processDocument(docId);
        }
    }

    private void sendDocumentProcessingMessage(Long docId) {
        try {
            rabbitTemplate.convertAndSend("knowledge.exchange", "knowledge.document.process", docId);
            log.info("文档处理任务已发送到消息队列: docId={}", docId);
        } catch (Exception e) {
            log.warn("消息队列发送失败，同步处理: docId={}", docId, e);
            processDocument(docId);
        }
    }

    private Long ensureProjectAttachmentKnowledgeBase(Long projectId, Long creatorId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getProjectId, projectId)
                .like(KnowledgeBase::getName, "项目附件知识库")
                .orderByDesc(KnowledgeBase::getId)
                .last("LIMIT 1"));
        if (kb != null) {
            return kb.getId();
        }

        KnowledgeBase created = new KnowledgeBase();
        created.setName("项目附件知识库-" + projectId);
        created.setDescription("系统自动归档需求流程附件");
        created.setProjectId(projectId);
        created.setCreatorId(uploaderOrZero(creatorId));
        created.setDocCount(0);
        created.setChunkCount(0);
        created.setStatus("active");
        knowledgeBaseMapper.insert(created);
        return created.getId();
    }

    private Long uploaderOrZero(Long uploaderId) {
        return uploaderId == null ? 0L : uploaderId;
    }

    private void recordShareAccess(KnowledgeDocumentShare share,
                                   Long accessUserId,
                                   String accessIp,
                                   String userAgent,
                                   String accessStatus,
                                   String failureReason) {
        KnowledgeDocumentShareLog logEntry = new KnowledgeDocumentShareLog();
        logEntry.setShareId(share.getId());
        logEntry.setDocumentId(share.getDocumentId());
        logEntry.setAccessUserId(accessUserId);
        logEntry.setAccessIp(accessIp);
        logEntry.setUserAgent(userAgent);
        logEntry.setAccessStatus(accessStatus);
        logEntry.setFailureReason(failureReason);
        shareLogMapper.insert(logEntry);
    }

    private Boolean asBoolean(Integer value) {
        return value != null && value == 1;
    }

    private ShareValidationResult validateShareForAccess(String token,
                                                         Long currentUserId,
                                                         String accessIp,
                                                         String userAgent,
                                                         boolean consumeAccess) {
        KnowledgeDocumentShare share = shareMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentShare>()
                .eq(KnowledgeDocumentShare::getToken, token)
                .last("LIMIT 1"));
        if (share == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享链接不存在");
        }
        if (!"active".equalsIgnoreCase(share.getStatus())) {
            recordShareAccess(share, currentUserId, accessIp, userAgent, "failed", "分享链接已失效");
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分享链接已失效");
        }
        if (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now())) {
            share.setStatus("expired");
            shareMapper.updateById(share);
            recordShareAccess(share, currentUserId, accessIp, userAgent, "failed", "分享链接已过期");
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分享链接已过期");
        }
        if (Boolean.TRUE.equals(asBoolean(share.getRequireLogin())) && currentUserId == null) {
            recordShareAccess(share, null, accessIp, userAgent, "failed", "需要登录访问");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "该分享链接需要登录后访问");
        }
        if (Boolean.TRUE.equals(asBoolean(share.getOneTimeAccess())) && share.getUsedCount() != null && share.getUsedCount() > 0) {
            share.setStatus("used");
            shareMapper.updateById(share);
            recordShareAccess(share, currentUserId, accessIp, userAgent, "failed", "分享链接已使用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "该分享链接已被使用");
        }

        KnowledgeDocument doc = documentMapper.selectById(share.getDocumentId());
        if (doc == null || !Objects.equals(doc.getKnowledgeBaseId(), share.getKnowledgeBaseId())) {
            recordShareAccess(share, currentUserId, accessIp, userAgent, "failed", "文档不存在");
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享文档不存在");
        }

        if (consumeAccess) {
            share.setUsedCount((share.getUsedCount() == null ? 0 : share.getUsedCount()) + 1);
            share.setUsedAt(LocalDateTime.now());
            if (Boolean.TRUE.equals(asBoolean(share.getOneTimeAccess()))) {
                share.setStatus("used");
            }
            shareMapper.updateById(share);
            recordShareAccess(share, currentUserId, accessIp, userAgent, "success", null);
        }

        return new ShareValidationResult(share, doc);
    }

    private String generateShareAccessToken(KnowledgeDocumentShare share, KnowledgeDocument doc, Long currentUserId) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("knowledge-share")
                .claim("shareId", share.getId())
                .claim("shareToken", share.getToken())
                .claim("knowledgeBaseId", share.getKnowledgeBaseId())
                .claim("documentId", share.getDocumentId())
                .claim("minioKey", doc.getMinioKey())
                .claim("userId", currentUserId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private ShareAccessClaims parseShareAccessToken(String shareAccessToken) {
        if (shareAccessToken == null || shareAccessToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "分享访问令牌缺失");
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(shareAccessToken)
                    .getPayload();
            return new ShareAccessClaims(
                    toLong(claims.get("shareId")),
                    (String) claims.get("shareToken"),
                    toLong(claims.get("knowledgeBaseId")),
                    toLong(claims.get("documentId")),
                    (String) claims.get("minioKey")
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "分享访问令牌无效");
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            return Long.parseLong(str);
        }
        return null;
    }

    private long resolveFileSize(KnowledgeDocument document) throws Exception {
        if (document.getFileSize() != null && document.getFileSize() > 0) {
            return document.getFileSize();
        }
        return minioStorageService.stat(document.getMinioKey()).size();
    }

    private String getContentType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain; charset=UTF-8";
            case "md" -> "text/markdown; charset=UTF-8";
            case "csv" -> "text/csv; charset=UTF-8";
            case "json" -> "application/json; charset=UTF-8";
            case "xml" -> "application/xml; charset=UTF-8";
            case "log" -> "text/plain; charset=UTF-8";
            case "yml", "yaml" -> "text/yaml; charset=UTF-8";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            default -> "application/octet-stream";
        };
    }

    private ByteRange parseRange(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        if (!rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) {
            return null;
        }

        String value = rangeHeader.substring("bytes=".length()).trim();
        String[] parts = value.split("-", 2);
        if (parts.length != 2) {
            return null;
        }

        try {
            long start;
            long end;

            if (parts[0].isBlank()) {
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength <= 0) {
                    return null;
                }
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(parts[0]);
                end = parts[1].isBlank() ? fileSize - 1 : Long.parseLong(parts[1]);
            }

            if (start < 0 || start >= fileSize || end < start) {
                return null;
            }

            return new ByteRange(start, Math.min(end, fileSize - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<float[]> embedInBatches(List<String> chunks) {
        List<float[]> allVectors = new ArrayList<>();
        int batchSize = 16;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            try {
                List<float[]> batchVectors = embeddingService.embed(batch);
                allVectors.addAll(batchVectors);
            } catch (Exception e) {
                log.warn("批量Embedding失败，降级为逐条处理: {}", e.getMessage());
                for (String text : batch) {
                    try {
                        allVectors.add(embeddingService.embed(text));
                    } catch (Exception ex) {
                        log.warn("单条Embedding失败，跳过: text长度={}", text.length());
                        allVectors.add(new float[0]);
                    }
                }
            }
        }
        return allVectors;
    }

    private static String sanitizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("\\t+", " ")
                .replaceAll(" +", " ")
                .trim();
    }

    private String readDocumentContent(KnowledgeDocument doc) throws Exception {
        String type = doc.getFileType();
        try (InputStream is = minioStorageService.download(doc.getMinioKey())) {
            return switch (type) {
                case "txt", "md" -> readText(is);
                case "pdf" -> readPdf(is);
                case "docx" -> readDocx(is);
                case "doc" -> readDoc(is);
                case "xlsx", "xls" -> readExcel(is);
                default -> readText(is);
            };
        }
    }

    private String readText(InputStream is) throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private String readPdf(InputStream is) throws Exception {
        try (var document = org.apache.pdfbox.Loader.loadPDF(is.readAllBytes())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String readDocx(InputStream is) throws Exception {
        try (var wb = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(wb);
            return extractor.getText();
        }
    }

    private String readDoc(InputStream is) throws Exception {
        try (var fs = new org.apache.poi.poifs.filesystem.POIFSFileSystem(is)) {
            var extractor = new org.apache.poi.hwpf.extractor.WordExtractor(fs);
            return extractor.getText();
        }
    }

    private String readExcel(InputStream is) throws Exception {
        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                var sheet = wb.getSheetAt(i);
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (var row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (var cell : row) {
                        cells.add(getCellText(cell));
                    }
                    sb.append(String.join("\t", cells)).append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String getCellText(org.apache.poi.ss.usermodel.Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = knowledgeConfig.getChunkSize();
        int overlap = knowledgeConfig.getChunkOverlap();

        String[] paragraphs = content.split("\\n\\n+");

        StringBuilder currentChunk = new StringBuilder();
        for (String para : paragraphs) {
            if (para.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                for (int i = 0; i < para.length(); i += chunkSize - overlap) {
                    String sub = para.substring(i, Math.min(i + chunkSize, para.length()));
                    if (!sub.isBlank()) {
                        chunks.add(sub.trim());
                    }
                }
                continue;
            }
            if (currentChunk.length() + para.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                String overlapText = getOverlapText(currentChunk.toString(), overlap);
                currentChunk = new StringBuilder(overlapText);
            }
            currentChunk.append(para).append("\n\n");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private String getOverlapText(String text, int overlapChars) {
        if (text.length() <= overlapChars) {
            return text;
        }
        return text.substring(text.length() - overlapChars);
    }

    private void updateKnowledgeBaseCount(Long knowledgeBaseId) {
        Long docCount = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId));
        Long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, knowledgeBaseId));

        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb != null) {
            kb.setDocCount(docCount.intValue());
            kb.setChunkCount(chunkCount.intValue());
            knowledgeBaseMapper.updateById(kb);
        }
    }

    private String extractFileType(String fileName) {
        if (fileName == null) return "unknown";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return "unknown";
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    @Override
    @Transactional
    public int retryDocuments(Long knowledgeBaseId, List<Long> documentIds) {
        int retried = 0;
        for (Long docId : documentIds) {
            KnowledgeDocument doc = documentMapper.selectById(docId);
            if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
                continue;
            }
            if (!"failed".equals(doc.getStatus())) {
                continue;
            }
            // 清理旧的分块数据
            chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                    .eq(KnowledgeChunk::getDocumentId, docId));
            try {
                milvusVectorStore.deleteByDocumentId(String.valueOf(docId));
            } catch (Exception e) {
                log.warn("Milvus向量清理失败: documentId={}", docId, e);
            }
            doc.setStatus("pending");
            doc.setErrorMessage(null);
            doc.setChunkCount(0);
            documentMapper.updateById(doc);
            enqueueDocumentProcessing(docId);
            retried++;
        }
        return retried;
    }

    @Override
    @Transactional
    public int batchDelete(Long knowledgeBaseId, List<Long> documentIds) {
        int deleted = 0;
        for (Long docId : documentIds) {
            try {
                delete(knowledgeBaseId, docId);
                deleted++;
            } catch (Exception e) {
                log.warn("批量删除文档失败: documentId={}", docId, e);
            }
        }
        return deleted;
    }

    @Override
    public String getPreviewUrl(Long knowledgeBaseId, Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        try {
            return minioStorageService.getPresignedUrl(doc.getMinioKey(), 1);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取预览地址失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadDocument(Long knowledgeBaseId, Long documentId, jakarta.servlet.http.HttpServletResponse response) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        try (InputStream is = minioStorageService.download(doc.getMinioKey())) {
            String encodedName = java.net.URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
            is.transferTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件下载失败: " + e.getMessage());
        }
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument doc) {
        String uploaderName = null;
        if (doc.getUploaderId() != null) {
            SysUser user = sysUserMapper.selectById(doc.getUploaderId());
            if (user != null) {
                uploaderName = user.getRealName();
            }
        }
        return KnowledgeDocumentVO.builder()
                .id(doc.getId())
                .knowledgeBaseId(doc.getKnowledgeBaseId())
                .projectId(doc.getProjectId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .requirementId(doc.getRequirementId())
                .sourceType(doc.getSourceType())
                .sourceId(doc.getSourceId())
                .uploaderId(doc.getUploaderId())
                .uploaderName(uploaderName)
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private record ShareValidationResult(KnowledgeDocumentShare share, KnowledgeDocument document) {
    }

    private record ShareAccessClaims(Long shareId, String shareToken, Long knowledgeBaseId, Long documentId, String minioKey) {
    }

    private record ByteRange(long start, long end) {
    }
}
