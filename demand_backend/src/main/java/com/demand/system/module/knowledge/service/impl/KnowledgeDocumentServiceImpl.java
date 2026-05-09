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
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.file.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SysUserMapper sysUserMapper;
    private final MinioStorageService minioStorageService;
    private final EmbeddingService embeddingService;
    private final KnowledgeConfig knowledgeConfig;
    private final MilvusVectorStore milvusVectorStore;
    private final RabbitTemplate rabbitTemplate;

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
        doc.setFileName(originalName);
        doc.setFileType(fileType);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus("pending");
        doc.setMinioKey(minioKey);
        doc.setUploaderId(uploaderId);
        documentMapper.insert(doc);

        // 通过RabbitMQ异步处理文档，事务提交后再发消息避免竞态
        try {
            Long docId = doc.getId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            rabbitTemplate.convertAndSend("knowledge.exchange", "knowledge.document.process", docId);
                            log.info("文档处理任务已发送到消息队列: docId={}", docId);
                        } catch (Exception e) {
                            log.warn("消息队列发送失败，同步处理: docId={}", docId, e);
                            processDocument(docId);
                        }
                    }
                });
            } else {
                rabbitTemplate.convertAndSend("knowledge.exchange", "knowledge.document.process", docId);
                log.info("文档处理任务已发送到消息队列: docId={}", docId);
            }
        } catch (Exception e) {
            log.warn("消息队列发送失败，同步处理: docId={}", doc.getId(), e);
            processDocument(doc.getId());
        }

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
    @Transactional
    public void processDocument(Long documentId) {
        KnowledgeDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return;
        }

        doc.setStatus("parsed");
        documentMapper.updateById(doc);

        // 读取文件内容
        String content;
        try {
            content = readDocumentContent(doc);
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage("文件读取失败: " + e.getMessage());
            documentMapper.updateById(doc);
            return;
        }

        // 分块
        List<String> chunks = splitContent(content);
        doc.setStatus("indexing");
        documentMapper.updateById(doc);

        // 生成向量并存储到MySQL和Milvus
        try {
            List<float[]> vectors = embeddingService.embed(chunks);
            List<MilvusVectorStore.VectorDocument> milvusDocs = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String vectorId = UUID.randomUUID().toString();

                // MySQL元数据
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(doc.getId());
                chunk.setKnowledgeBaseId(doc.getKnowledgeBaseId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setCharCount(chunks.get(i).length());
                chunk.setVectorId(vectorId);
                chunkMapper.insert(chunk);

                // Milvus向量
                milvusDocs.add(new MilvusVectorStore.VectorDocument(
                        vectorId, vectors.get(i),
                        doc.getKnowledgeBaseId(), doc.getId(),
                        i, chunks.get(i), null, null,
                        doc.getFileName(), doc.getFileType()
                ));
            }

            // 批量写入Milvus
            milvusVectorStore.insertVectors(milvusDocs);

        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage("向量化失败: " + e.getMessage());
            documentMapper.updateById(doc);
            return;
        }

        doc.setChunkCount(chunks.size());
        doc.setStatus("indexed");
        documentMapper.updateById(doc);

        // 更新知识库计数
        updateKnowledgeBaseCount(doc.getKnowledgeBaseId());
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
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .uploaderId(doc.getUploaderId())
                .uploaderName(uploaderName)
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
