package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.security.PermissionGuard;
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
import com.demand.system.module.knowledge.support.KnowledgeDocumentSupport;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.preview.PreviewWarmupService;
import com.demand.system.module.file.storage.MinioStorageService;
import com.demand.system.module.requirement.dto.RequirementAttachmentDTO;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * xlsx/xls 大文件建索引时的文件大小阈值（字节）。
     *
     * <p>超过该阈值直接走 {@code stored} 路径，仅保存文件、不入 embedding 队列，
     * 避免 20+ MB xlsx 在 readExcel + embedInBatches 阶段把处理线程卡到超时阈值。
     * 阈值参考：东莞延伸清单 23.2 MB xlsx 三次重试均卡死。</p>
     */
    private static final long XLSX_INDEX_SIZE_LIMIT = 30L * 1024 * 1024;

    /**
     * 单次 readExcel 解析时最多扫描的行数（防止几万行大表拖垮解析阶段）。
     */
    private static final int EXCEL_MAX_ROWS = 50_000;

    /**
     * 单次 readExcel 解析时最多产出的 chunk 数（防止几万 chunks 把 embedding 阶段拖到 20 分钟以上）。
     */
    private static final int EXCEL_MAX_CHUNKS = 5_000;

    /**
     * POI zip entry 字节上限（兜底，防止 200MB+ zip entry 触发 OOM）。
     *
     * <p>原方案设 {@link Integer#MAX_VALUE} 会让超大 embedded image 把堆撑爆；
     * 200MB 已经覆盖几乎所有正常 xlsx，超此值直接报错退出 readExcel 即可。</p>
     */
    private static final int POI_BYTE_ARRAY_MAX_OVERRIDE = 200 * 1024 * 1024;

    /**
     * 预览 URL 默认有效期（小时）。
     *
     * <p>原为 1 小时，但 kkFileView 异步转码 + 浏览器渲染链路总耗时可能超过 1 小时，
     * 改为 24 小时兼容正常使用窗口。短任务不会变慢，URL 在用户重新打开预览时会重新签发。</p>
     */
    private static final int DEFAULT_PREVIEW_PRESIGN_HOURS = 24;

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
    private final com.demand.system.module.project.mapper.ProjectMapper projectMapper;
    private final PreviewWarmupService previewWarmupService;
    private final com.demand.system.module.knowledge.mapper.KnowledgeDocumentRequirementRefMapper refMapper;
    private final com.demand.system.module.knowledge.service.KnowledgeBaseService knowledgeBaseService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public KnowledgeDocumentServiceImpl(KnowledgeDocumentMapper documentMapper,
                                        KnowledgeChunkMapper chunkMapper,
                                        KnowledgeBaseMapper knowledgeBaseMapper,
                                        SysUserMapper sysUserMapper,
                                        RequirementMapper requirementMapper,
                                        MinioStorageService minioStorageService,
                                        EmbeddingService embeddingService,
                                        KnowledgeConfig knowledgeConfig,
                                        MilvusVectorStore milvusVectorStore,
                                        RabbitTemplate rabbitTemplate,
                                        KnowledgeDocumentShareMapper shareMapper,
                                        KnowledgeDocumentShareLogMapper shareLogMapper,
                                        com.demand.system.module.project.mapper.ProjectMapper projectMapper,
                                        PreviewWarmupService previewWarmupService,
                                        com.demand.system.module.knowledge.mapper.KnowledgeDocumentRequirementRefMapper refMapper,
                                        com.demand.system.module.knowledge.service.KnowledgeBaseService knowledgeBaseService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.sysUserMapper = sysUserMapper;
        this.requirementMapper = requirementMapper;
        this.minioStorageService = minioStorageService;
        this.embeddingService = embeddingService;
        this.knowledgeConfig = knowledgeConfig;
        this.milvusVectorStore = milvusVectorStore;
        this.rabbitTemplate = rabbitTemplate;
        this.shareMapper = shareMapper;
        this.shareLogMapper = shareLogMapper;
        this.projectMapper = projectMapper;
        this.previewWarmupService = previewWarmupService;
        this.refMapper = refMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long uploaderId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }

        String originalName = file.getOriginalFilename();
        String fileType = extractFileType(originalName);
        if (!KnowledgeDocumentSupport.isSupported(fileType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该文件格式暂不支持上传预览: " + (originalName == null ? "未知文件" : originalName));
        }
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
        boolean vectorizable = KnowledgeDocumentSupport.isVectorizable(fileType)
                && !isTooLargeForIndexing(fileType, file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(vectorizable ? "pending" : "stored");
        doc.setMinioKey(minioKey);
        doc.setSourceType("knowledge_base");
        doc.setSourceId(knowledgeBaseId);
        doc.setUploaderId(uploaderId);
        doc.setDownloadCount(0);
        documentMapper.insert(doc);
        enqueuePreviewWarmup(doc);
        if (vectorizable) {
            enqueueDocumentProcessing(doc.getId());
        }

        return toVO(doc);
    }

    /**
     * xlsx/xls 大文件跳过 embedding 队列的判断。
     *
     * <p>超过 {@link #XLSX_INDEX_SIZE_LIMIT} 的 xlsx/xls 直接走 stored 路径，
     * 仅保存文件不做向量化。其它类型暂不限制（DOCX/PDF 等场景下大文件也能正常解析）。
     * 仍可通过文件下载 / kkFileView 预览访问。</p>
     */
    private boolean isTooLargeForIndexing(String fileType, long fileSize) {
        if (fileType == null) {
            return false;
        }
        String lower = fileType.toLowerCase();
        return ("xlsx".equals(lower) || "xls".equals(lower)) && fileSize > XLSX_INDEX_SIZE_LIMIT;
    }

    @Override
    @Transactional
    public void syncRequirementAttachments(Long projectId, Long requirementId, List<RequirementAttachmentDTO> attachments, Long uploaderId) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        Long kbId = ensureProjectAttachmentKnowledgeBase(projectId, uploaderId);
        for (RequirementAttachmentDTO attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String fileType = extractFileType(attachment.getName());
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setKnowledgeBaseId(kbId);
            doc.setProjectId(projectId);
            doc.setRequirementId(requirementId);
            doc.setFileName(attachment.getName());
            doc.setFileType(fileType);
            doc.setFileSize(attachment.getSize());
            doc.setChunkCount(0);
            doc.setStatus(KnowledgeDocumentSupport.isSupported(fileType)
                    ? (KnowledgeDocumentSupport.isVectorizable(fileType) ? "pending" : "stored")
                    : "failed");
            doc.setErrorMessage(KnowledgeDocumentSupport.isSupported(fileType) ? null : "该文件格式暂不支持在线预览");
            doc.setMinioKey(attachment.getObjectName());
            doc.setSourceType("requirement");
            doc.setSourceId(requirementId);
            doc.setUploaderId(uploaderId);
            doc.setDownloadCount(0);
            documentMapper.insert(doc);
            enqueuePreviewWarmup(doc);
            if ("pending".equals(doc.getStatus())) {
                enqueueDocumentProcessing(doc.getId());
            }
        }
        updateKnowledgeBaseCount(kbId);
    }

    @Override
    @Transactional
    public void syncRequirementAttachmentsWithContext(Long projectId, Long requirementId, String requirementCode,
                                                     String requirementTitle, List<RequirementAttachmentDTO> attachments, Long uploaderId) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        Long targetKbId = getTargetKnowledgeBaseForRequirements(projectId, uploaderId);
        syncRequirementAttachmentsToKnowledgeBase(targetKbId, projectId, requirementId, requirementCode, requirementTitle, attachments, uploaderId);
    }

    @Override
    @Transactional
    public void syncRequirementAttachmentsToKnowledgeBase(Long knowledgeBaseId,
                                                          Long projectId,
                                                          Long requirementId,
                                                          String requirementCode,
                                                          String requirementTitle,
                                                          List<RequirementAttachmentDTO> attachments,
                                                          Long uploaderId) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (knowledgeBaseId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标知识库不能为空");
        }

        KnowledgeBase targetKb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (targetKb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标知识库不存在");
        }

        for (RequirementAttachmentDTO attachment : attachments) {
            if (attachment == null) {
                continue;
            }

            String fileType = extractFileType(attachment.getName());
            KnowledgeDocument existingDoc = findDocumentByFileNameAndSize(
                    knowledgeBaseId,
                    attachment.getName(),
                    attachment.getSize()
            );

            if (existingDoc != null) {
                addRequirementReference(existingDoc.getId(), requirementId, requirementCode, requirementTitle);
                log.info("文件已存在知识库，添加需求引用: kbId={}, docId={}, reqId={}, fileName={}",
                        knowledgeBaseId, existingDoc.getId(), requirementId, attachment.getName());
            } else {
                KnowledgeDocument doc = new KnowledgeDocument();
                doc.setKnowledgeBaseId(knowledgeBaseId);
                doc.setProjectId(projectId != null ? projectId : targetKb.getProjectId());
                doc.setRequirementId(requirementId);
                doc.setFileName(attachment.getName());
                doc.setFileType(fileType);
                doc.setFileSize(attachment.getSize());
                doc.setChunkCount(0);
                doc.setStatus(KnowledgeDocumentSupport.isSupported(fileType)
                        ? (KnowledgeDocumentSupport.isVectorizable(fileType) ? "pending" : "stored")
                        : "failed");
                doc.setErrorMessage(KnowledgeDocumentSupport.isSupported(fileType) ? null : "该文件格式暂不支持在线预览");
                doc.setMinioKey(attachment.getObjectName());
                doc.setSourceType("requirement");
                doc.setSourceId(requirementId);
                doc.setUploaderId(uploaderId);
                doc.setDownloadCount(0);
                documentMapper.insert(doc);

                addRequirementReference(doc.getId(), requirementId, requirementCode, requirementTitle);
                enqueuePreviewWarmup(doc);
                if ("pending".equals(doc.getStatus())) {
                    enqueueDocumentProcessing(doc.getId());
                }

                log.info("新文档入库: kbId={}, docId={}, reqId={}, fileName={}",
                        knowledgeBaseId, doc.getId(), requirementId, attachment.getName());
            }
        }

        updateKnowledgeBaseCount(knowledgeBaseId);
    }

    @Override
    public List<com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef> getDocumentRequirementRefs(Long documentId) {
        return refMapper.selectList(new LambdaQueryWrapper<com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef>()
                .eq(com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef::getDocumentId, documentId)
                .orderByDesc(com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef::getCreatedAt));
    }

    /**
     * 确定需求文件的目标知识库（优先使用默认知识库）
     */
    private Long getTargetKnowledgeBaseForRequirements(Long projectId, Long creatorId) {
        // 优先查找默认知识库
        Long defaultKbId = knowledgeBaseService.getDefaultKnowledgeBaseIdForRequirements();
        if (defaultKbId != null) {
            return defaultKbId;
        }
        // 否则使用原有的项目知识库逻辑
        return ensureProjectAttachmentKnowledgeBase(projectId, creatorId);
    }

    /**
     * 根据文件名和大小查找文档（判重）
     */
    private KnowledgeDocument findDocumentByFileNameAndSize(Long knowledgeBaseId, String fileName, Long fileSize) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KnowledgeDocument::getFileName, fileName)
                .eq(KnowledgeDocument::getFileSize, fileSize)
                .last("LIMIT 1"));
    }

    /**
     * 添加文档与需求的引用关系
     */
    private void addRequirementReference(Long documentId, Long requirementId, String requirementCode, String requirementTitle) {
        // 检查是否已存在引用
        Long count = refMapper.selectCount(new LambdaQueryWrapper<com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef>()
                .eq(com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef::getDocumentId, documentId)
                .eq(com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef::getRequirementId, requirementId));

        if (count > 0) {
            return; // 引用已存在，跳过
        }

        com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef ref =
                new com.demand.system.module.knowledge.entity.KnowledgeDocumentRequirementRef();
        ref.setDocumentId(documentId);
        ref.setRequirementId(requirementId);
        ref.setRequirementCode(requirementCode);
        ref.setRequirementTitle(requirementTitle);
        refMapper.insert(ref);
    }

    @Override
    public PageResult<KnowledgeDocumentVO> list(Long knowledgeBaseId,
                                                int pageNum,
                                                int pageSize,
                                                String fileName,
                                                String status,
                                                String createdAtStart,
                                                String createdAtEnd,
                                                String projectName,
                                                Long requirementId) {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        if (fileName != null && !fileName.isBlank()) {
            wrapper.like(KnowledgeDocument::getFileName, fileName.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(KnowledgeDocument::getStatus, status.trim());
        }
        if (requirementId != null) {
            wrapper.eq(KnowledgeDocument::getRequirementId, requirementId);
        }
        if (projectName != null && !projectName.isBlank()) {
            List<Long> matchedProjectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<com.demand.system.module.project.entity.Project>()
                            .like(com.demand.system.module.project.entity.Project::getName, projectName.trim())
                            .select(com.demand.system.module.project.entity.Project::getId)
            ).stream().map(com.demand.system.module.project.entity.Project::getId).collect(Collectors.toList());
            if (matchedProjectIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(KnowledgeDocument::getProjectId, matchedProjectIds);
        }
        LocalDateTime start = parseDateTime(createdAtStart, false);
        LocalDateTime end = parseDateTime(createdAtEnd, true);
        if (start != null) {
            wrapper.ge(KnowledgeDocument::getCreatedAt, start);
        }
        if (end != null) {
            wrapper.le(KnowledgeDocument::getCreatedAt, end);
        }
        wrapper.orderByDesc(KnowledgeDocument::getCreatedAt);
        Page<KnowledgeDocument> page = documentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<KnowledgeDocumentVO> voList = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public void delete(Long knowledgeBaseId, Long documentId) {
        assertCanModifyKnowledgeBase(knowledgeBaseId);
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }

        try {
            milvusVectorStore.deleteByDocumentId(String.valueOf(documentId));
        } catch (Exception e) {
            log.warn("Milvus向量删除失败: documentId={}", documentId, e);
        }

        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));

        if (doc.getMinioKey() != null) {
            try {
                minioStorageService.delete(doc.getMinioKey());
            } catch (Exception e) {
                log.warn("MinIO文件删除失败: {}", doc.getMinioKey(), e);
            }
        }

        documentMapper.deleteById(documentId);

        updateKnowledgeBaseCount(knowledgeBaseId);
    }

    @Override
    public String generateShareLink(Long knowledgeBaseId,
                                    Long documentId,
                                    Integer expireHours,
                                    Boolean requireLogin,
                                    Boolean oneTimeAccess,
                                    Long creatorId) {
        assertCanModifyKnowledgeBase(knowledgeBaseId);
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
    public String resolveShareAccessUrl(String token) {
        return "/public/share/" + token;
    }

    @Override
    @Transactional
    public KnowledgePublicShareContextVO getPublicShareContext(String token, Long currentUserId, String accessIp, String userAgent) {
        ShareValidationResult result = validateShareForAccess(token, currentUserId, accessIp, userAgent, true);
        String accessToken = generateShareAccessToken(result.share(), result.document(), currentUserId);

        String previewUrl = null;
        try {
            previewUrl = minioStorageService.getPresignedUrlForDocker(result.document().getMinioKey(), DEFAULT_PREVIEW_PRESIGN_HOURS);
        } catch (Exception e) {
            log.warn("生成分享文档预签名 URL 失败: {}", e.getMessage());
        }

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
                .previewUrl(previewUrl)
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
            if (download && !headOnly) {
                incrementDownloadCount(document.getId());
            }
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
    public void processDocument(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }

        // 重入 processDocument 时清空历史错误信息，避免残留"20 分钟超时"等误导性错误。
        // 同时刷新 updated_at，挡住超时检查器在 20 分钟内再次误判（搜索以 updated_at < 阈值为准）。
        if (doc.getErrorMessage() != null) {
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }

        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
        if (!KnowledgeDocumentSupport.isSupported(fileType)) {
            doc.setChunkCount(0);
            doc.setStatus("failed");
            doc.setErrorMessage("该文件格式暂不支持在线预览");
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        if (!KnowledgeDocumentSupport.isVectorizable(fileType)) {
            doc.setChunkCount(0);
            doc.setStatus("stored");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        doc.setStatus("parsed");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        List<String> chunks;
        try {
            chunks = readDocumentContent(doc);
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage("文件读取失败: " + e.getMessage());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            return;
        }

        // 检查是否是密码保护提示
        if (chunks.size() == 1 && "[此文档受密码保护，无法建立索引]".equals(chunks.get(0))) {
            doc.setChunkCount(0);
            doc.setStatus("failed");
            doc.setErrorMessage("此文档受密码保护，无法建立索引。请移除密码后重新上传。");
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        if (chunks == null || chunks.isEmpty()) {
            doc.setChunkCount(0);
            doc.setStatus("stored");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        chunks = chunks.stream()
                .map(KnowledgeDocumentServiceImpl::sanitizeText)
                .filter(c -> !c.isBlank())
                .toList();
        if (chunks.isEmpty()) {
            doc.setChunkCount(0);
            doc.setStatus("stored");
            doc.setErrorMessage(null);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
            return;
        }

        doc.setStatus("indexing");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

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

                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(doc.getId());
                chunk.setKnowledgeBaseId(doc.getKnowledgeBaseId());
                chunk.setChunkIndex(validChunks - 1);
                chunk.setContent(chunks.get(i));
                chunk.setCharCount(chunks.get(i).length());
                chunk.setVectorId(vectorId);
                chunkMapper.insert(chunk);

                milvusDocs.add(new MilvusVectorStore.VectorDocument(
                        vectorId, vector,
                        doc.getKnowledgeBaseId(), doc.getId(),
                        validChunks - 1, chunks.get(i), null, null,
                        doc.getFileName(), doc.getFileType()
                ));
            }

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
            log.error("消息队列发送失败，同步处理: docId={}", docId, e);
            processDocument(docId);
        }
    }

    private void enqueuePreviewWarmup(KnowledgeDocument doc) {
        if (doc == null || !KnowledgeDocumentSupport.needsKkFileViewWarmup(doc.getFileType())) {
            return;
        }
        Runnable warmupTask = () -> {
            try {
                String previewUrl = minioStorageService.getPresignedUrlForDocker(doc.getMinioKey(), DEFAULT_PREVIEW_PRESIGN_HOURS);
                previewWarmupService.warmup(previewUrl, doc.getFileName());
            } catch (Exception e) {
                log.warn("文档预览预热准备失败: docId={}", doc.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    warmupTask.run();
                }
            });
        } else {
            warmupTask.run();
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

    private List<String> readDocumentContent(KnowledgeDocument doc) throws Exception {
        String type = doc.getFileType();
        try (InputStream is = minioStorageService.download(doc.getMinioKey())) {
            if (KnowledgeDocumentSupport.isDirectTextPreview(type)) {
                // 纯文本预览走原文 + splitContent 路径
                return splitContent(readText(is));
            }
            // xlsx/xls 走 readExcel 流式切片（直接产出 chunks，不拼全文）
            if ("xlsx".equals(type) || "xls".equals(type)) {
                return readExcel(is);
            }
            // pdf/docx/doc 仍按原文读出，由 splitContent 切片
            String raw = switch (type) {
                case "pdf" -> readPdf(is);
                case "docx" -> readDocx(is);
                case "doc" -> readDoc(is);
                default -> "";
            };
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return splitContent(raw);
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
        // PDFBox 默认对单个 record 限制 100MB，超大 PDF（image stream / 字体 / xref 等）
        // 会抛 IOException。使用 setupMixed 取消内存限制并允许溢出到临时文件，
        // 同时 try-catch 兜底，避免单个大 PDF 拖垮整个文档索引流程。
        MemoryUsageSetting memUsage = MemoryUsageSetting.setupMixed(Integer.MAX_VALUE);
        try (var document = Loader.loadPDF(is.readAllBytes(), null, null, null, memUsage.streamCache)) {
            var stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            log.warn("PDF 文件受密码保护，无法提取文本: {}", e.getMessage());
            return "[此文档受密码保护，无法建立索引]";
        } catch (java.io.IOException e) {
            // PDFBox 3.x 解析异常统一为 IOException，密码保护场景已由 InvalidPasswordException 覆盖
            log.error("PDF 内容解析失败（文件可能损坏或格式异常）", e);
            return "";
        } catch (Exception e) {
            log.error("PDF 内容解析失败", e);
            return "";
        }
    }

    private String readDocx(InputStream is) throws Exception {
        try (var wb = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(wb);
            return extractor.getText();
        } catch (org.apache.poi.EncryptedDocumentException e) {
            log.warn("DOCX 文件受密码保护，无法提取文本: {}", e.getMessage());
            return "[此文档受密码保护，无法建立索引]";
        } catch (Exception e) {
            log.error("DOCX 内容解析失败", e);
            return "";
        }
    }

    private String readDoc(InputStream is) throws Exception {
        try (var fs = new org.apache.poi.poifs.filesystem.POIFSFileSystem(is)) {
            var extractor = new org.apache.poi.hwpf.extractor.WordExtractor(fs);
            return extractor.getText();
        } catch (org.apache.poi.EncryptedDocumentException e) {
            log.warn("DOC 文件受密码保护，无法提取文本: {}", e.getMessage());
            return "[此文档受密码保护，无法建立索引]";
        } catch (Exception e) {
            log.error("DOC 内容解析失败", e);
            return "";
        }
    }

    private List<String> readExcel(InputStream is) {
        // POI 默认对单个 zip entry 限制 100MB，超大 xlsx（embedded image / shared string / sheet data）
        // 会抛 IOException。POI 的 setByteArrayMaxOverride 是 static 方法，
        // 设置后对 readDocx/readDoc 等所有 POI 解析生效。
        // 上限 200MB 已经覆盖几乎所有正常 xlsx，超过此值直接抛错退出 readExcel 即可，
        // 避免 Integer.MAX_VALUE 导致 embedded image 把堆撑爆。
        // 同时按 sheet 流式切片 + 硬性行数/chunk 数双上限，避免 23MB+ xlsx 解析后
        // 把整个 StringBuilder 撑爆堆内存或拖到 20 分钟超时阈值。
        org.apache.poi.util.IOUtils.setByteArrayMaxOverride(POI_BYTE_ARRAY_MAX_OVERRIDE);
        List<String> chunks = new ArrayList<>();
        int chunkSize = knowledgeConfig.getChunkSize();
        int chunkOverlap = knowledgeConfig.getChunkOverlap();
        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
            for (int si = 0; si < wb.getNumberOfSheets() && chunks.size() < EXCEL_MAX_CHUNKS; si++) {
                var sheet = wb.getSheetAt(si);
                if (sheet == null) {
                    continue;
                }
                StringBuilder sheetBuilder = new StringBuilder();
                sheetBuilder.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                int rowCount = 0;
                for (var row : sheet) {
                    // 行数 / chunk 数任一到达上限就立刻停止，避免几十万行 / 几万 chunks 把处理线程拖到 20 分钟超时
                    if (++rowCount > EXCEL_MAX_ROWS || chunks.size() >= EXCEL_MAX_CHUNKS) {
                        log.warn("Excel 解析触发硬上限: rowCount={}, chunks={}, maxRows={}, maxChunks={}",
                                rowCount, chunks.size(), EXCEL_MAX_ROWS, EXCEL_MAX_CHUNKS);
                        break;
                    }
                    List<String> cells = new ArrayList<>();
                    for (var cell : row) {
                        cells.add(getCellText(cell));
                    }
                    sheetBuilder.append(String.join("\t", cells)).append('\n');

                    // 单 sheet 累积到 chunkSize 时立即切片并清空，限制内存峰值
                    if (sheetBuilder.length() >= chunkSize) {
                        chunks.addAll(splitText(sheetBuilder.toString(), chunkSize, chunkOverlap));
                        sheetBuilder.setLength(0);
                        if (chunks.size() >= EXCEL_MAX_CHUNKS) {
                            break;
                        }
                    }
                }
                // sheet 收尾的残余文本也作为一个 chunk
                if (sheetBuilder.length() > 0) {
                    chunks.addAll(splitText(sheetBuilder.toString(), chunkSize, chunkOverlap));
                }
            }
            return chunks;
        } catch (Exception e) {
            log.error("Excel 内容解析失败（文件可能包含超大 zip entry 或已损坏）", e);
            return chunks; // 失败时返回已解析的 chunks（可能为空），不再让上游抛错
        }
    }

    /**
     * 按 chunkSize + chunkOverlap 把文本切成多个 chunk。逻辑与 {@link #splitContent} 一致，
     * 但只接受文本片段（不调用 split("\\n\\n+")），专供 readExcel 按 sheet 流式切片使用。
     */
    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (int i = 0; i < text.length(); i += chunkSize - chunkOverlap) {
            String sub = text.substring(i, Math.min(i + chunkSize, text.length()));
            String trimmed = sub.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
            if (i + chunkSize >= text.length()) {
                break;
            }
        }
        return result;
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

    /**
     * 行级权限：校验当前用户对知识库有写入权限（超管或创建人）。
     */
    private void assertCanModifyKnowledgeBase(Long knowledgeBaseId) {
        PermissionGuard.requireKnowledgeBaseOwner(knowledgeBaseMapper, knowledgeBaseId,
                PermissionGuard.requireCurrentUserId(),
                com.demand.system.module.auth.security.SecurityUtils.isSuperAdmin());
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
        assertCanModifyKnowledgeBase(knowledgeBaseId);
        int retried = 0;
        for (Long docId : documentIds) {
            KnowledgeDocument doc = documentMapper.selectById(docId);
            if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
                continue;
            }
            if (!"failed".equals(doc.getStatus()) || !KnowledgeDocumentSupport.isVectorizable(doc.getFileType())) {
                continue;
            }
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
        assertCanModifyKnowledgeBase(knowledgeBaseId);
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
    @Transactional
    public void skipIndexing(Long knowledgeBaseId, Long documentId) {
        assertCanModifyKnowledgeBase(knowledgeBaseId);
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        // 清理已有的 chunks 和 Milvus 向量
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        try {
            milvusVectorStore.deleteByDocumentId(String.valueOf(documentId));
        } catch (Exception e) {
            log.warn("Milvus向量清理失败: documentId={}", documentId, e);
        }
        doc.setStatus("stored");
        doc.setChunkCount(0);
        doc.setErrorMessage("已跳过索引，仅保留文件存储。如需建立索引请手动重传。");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
        updateKnowledgeBaseCount(knowledgeBaseId);
        log.info("已跳过文档索引: id={}, name={}", doc.getId(), doc.getFileName());
    }

    @Override
    public String getPreviewUrl(Long knowledgeBaseId, Long documentId) {
        return getPreviewUrl(knowledgeBaseId, documentId, DEFAULT_PREVIEW_PRESIGN_HOURS);
    }

    @Override
    public String getPreviewUrl(Long knowledgeBaseId, Long documentId, int expiryHours) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getKnowledgeBaseId().equals(knowledgeBaseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        try {
            return minioStorageService.getPresignedUrlForDocker(doc.getMinioKey(), expiryHours);
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
            incrementDownloadCount(doc.getId());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public void batchDownloadDocuments(Long knowledgeBaseId, List<Long> documentIds, jakarta.servlet.http.HttpServletResponse response) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要下载的文档");
        }

        List<KnowledgeDocument> documents = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                        .in(KnowledgeDocument::getId, documentIds)
        );

        if (documents.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到可下载的文档");
        }

        try (OutputStream os = response.getOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(os))) {

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''download.zip");

            for (KnowledgeDocument doc : documents) {
                if (doc.getMinioKey() == null || doc.getMinioKey().isBlank()) {
                    log.warn("文档无存储路径，跳过: documentId={}", doc.getId());
                    continue;
                }

                try (InputStream is = new BufferedInputStream(minioStorageService.download(doc.getMinioKey()))) {
                    String entryName = sanitizeFileName(doc.getFileName());
                    zipOut.putNextEntry(new ZipEntry(entryName));
                    is.transferTo(zipOut);
                    zipOut.closeEntry();
                } catch (Exception e) {
                    log.warn("文档下载失败，跳过: documentId={}, error={}", doc.getId(), e.getMessage());
                }
            }

            zipOut.finish();
            os.flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "批量下载失败: " + e.getMessage());
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static class BufferedOutputStream extends java.io.BufferedOutputStream {
        public BufferedOutputStream(OutputStream out) {
            super(out);
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
        String projectName = null;
        if (doc.getProjectId() != null) {
            com.demand.system.module.project.entity.Project project = projectMapper.selectById(doc.getProjectId());
            if (project != null) {
                projectName = project.getName();
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
                .projectName(projectName)
                .downloadCount(doc.getDownloadCount() == null ? 0 : doc.getDownloadCount())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private void incrementDownloadCount(Long documentId) {
        documentMapper.update(
                null,
                new LambdaUpdateWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getId, documentId)
                        .setSql("download_count = COALESCE(download_count, 0) + 1")
        );
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                var date = java.time.LocalDate.parse(trimmed, DATE_FORMATTER);
                return LocalDateTime.of(date, endOfDay ? LocalTime.MAX : LocalTime.MIN);
            } catch (DateTimeParseException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "时间范围格式错误");
            }
        }
    }

    private record ShareValidationResult(KnowledgeDocumentShare share, KnowledgeDocument document) {
    }

    private record ShareAccessClaims(Long shareId, String shareToken, Long knowledgeBaseId, Long documentId, String minioKey) {
    }

    private record ByteRange(long start, long end) {
    }
}
