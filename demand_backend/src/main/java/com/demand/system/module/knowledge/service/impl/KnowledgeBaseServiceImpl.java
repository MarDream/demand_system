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
import com.demand.system.module.knowledge.dto.KnowledgeBaseCreateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseMigrateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseUpdateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseVO;
import com.demand.system.module.knowledge.dto.KnowledgeMigrateResultVO;
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.entity.KnowledgeChunk;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.KnowledgeBaseService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.file.storage.MinioStorageService;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final MinioStorageService minioStorageService;
    private final MilvusVectorStore milvusVectorStore;
    private final SysUserMapper sysUserMapper;
    private final ProjectMapper projectMapper;
    private final RabbitTemplate rabbitTemplate;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                   KnowledgeChunkMapper knowledgeChunkMapper,
                                   KnowledgeDocumentMapper knowledgeDocumentMapper,
                                   MinioStorageService minioStorageService,
                                   MilvusVectorStore milvusVectorStore,
                                   SysUserMapper sysUserMapper,
                                   ProjectMapper projectMapper,
                                   RabbitTemplate rabbitTemplate) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.minioStorageService = minioStorageService;
        this.milvusVectorStore = milvusVectorStore;
        this.sysUserMapper = sysUserMapper;
        this.projectMapper = projectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto, Long creatorId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setProjectId(dto.getProjectId());
        kb.setCreatorId(creatorId);
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kb.setStatus("active");
        knowledgeBaseMapper.insert(kb);
        return toVO(kb);
    }

    @Override
    public KnowledgeBaseVO getById(Long id) {
        KnowledgeBase kb = findOrThrow(id);
        return toVO(kb);
    }

    @Override
    public PageResult<KnowledgeBaseVO> list(String name, int pageNum, int pageSize) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(KnowledgeBase::getName, name);
        }
        wrapper.orderByDesc(KnowledgeBase::getCreatedAt);
        Page<KnowledgeBase> page = knowledgeBaseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<KnowledgeBaseVO> voList = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional
    public KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateDTO dto) {
        PermissionGuard.requireKnowledgeBaseOwner(knowledgeBaseMapper, id,
                PermissionGuard.requireCurrentUserId(),
                com.demand.system.module.auth.security.SecurityUtils.isSuperAdmin());
        KnowledgeBase kb = findOrThrow(id);
        if (dto.getName() != null) {
            kb.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            kb.setDescription(dto.getDescription());
        }
        knowledgeBaseMapper.updateById(kb);
        return toVO(kb);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PermissionGuard.requireKnowledgeBaseOwner(knowledgeBaseMapper, id,
                PermissionGuard.requireCurrentUserId(),
                com.demand.system.module.auth.security.SecurityUtils.isSuperAdmin());
        findOrThrow(id);

        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id));

        try {
            milvusVectorStore.deleteByKnowledgeBaseId(String.valueOf(id));
        } catch (Exception e) {
            log.warn("Milvus知识库向量删除失败: knowledgeBaseId={}", id, e);
        }

        for (KnowledgeDocument document : documents) {
            if (document.getMinioKey() == null || document.getMinioKey().isBlank()) {
                continue;
            }
            try {
                minioStorageService.delete(document.getMinioKey());
            } catch (Exception e) {
                log.warn("MinIO知识库文档删除失败: minioKey={}", document.getMinioKey(), e);
            }
        }

        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getKnowledgeBaseId, id));
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, id));
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public KnowledgeMigrateResultVO migrateDocuments(Long sourceId, KnowledgeBaseMigrateDTO dto) {
        if (sourceId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源知识库 ID 不能为空");
        }
        if (dto == null || dto.getTargetKnowledgeBaseId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标知识库 ID 不能为空");
        }
        if (sourceId.equals(dto.getTargetKnowledgeBaseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源与目标知识库不能相同");
        }

        // 行级权限：只有源知识库的创建人（或者超管）可以发起迁移
        PermissionGuard.requireKnowledgeBaseOwner(knowledgeBaseMapper, sourceId,
                PermissionGuard.requireCurrentUserId(),
                com.demand.system.module.auth.security.SecurityUtils.isSuperAdmin());

        KnowledgeBase source = findOrThrow(sourceId);
        KnowledgeBase target = findOrThrow(dto.getTargetKnowledgeBaseId());
        if (!"active".equals(source.getStatus()) || !"active".equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源/目标知识库必须处于活跃状态");
        }

        // 1) 选定要迁移的文档
        List<KnowledgeDocument> sourceDocs = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getKnowledgeBaseId, sourceId));
        if (CollectionUtils.isEmpty(sourceDocs)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "源知识库下没有文档可迁移");
        }

        List<KnowledgeDocument> toMigrate;
        if (CollectionUtils.isEmpty(dto.getDocumentIds())) {
            toMigrate = sourceDocs;
        } else {
            java.util.Set<Long> ids = new java.util.HashSet<>(dto.getDocumentIds());
            toMigrate = sourceDocs.stream()
                    .filter(d -> ids.contains(d.getId()))
                    .collect(Collectors.toList());
            if (toMigrate.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "所选文档均不属于该知识库");
            }
        }

        List<Long> docIds = toMigrate.stream().map(KnowledgeDocument::getId).collect(Collectors.toList());

        // 2) 统计被影响 chunks 数量（迁移前）
        Long chunkCountToMigrate = knowledgeChunkMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeChunk>().in(KnowledgeChunk::getDocumentId, docIds));
        int chunkCount = chunkCountToMigrate == null ? 0 : chunkCountToMigrate.intValue();

        // 3) 统计总 chunk 大小（按 doc 聚合的 chunkCount 之和，用于调整 docCount/chunkCount）
        int docSumChunkCount = toMigrate.stream()
                .mapToInt(d -> d.getChunkCount() == null ? 0 : d.getChunkCount())
                .sum();

        // 4) 更新 documents / chunks 的 knowledge_base_id
        knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .set(KnowledgeDocument::getKnowledgeBaseId, target.getId())
                .in(KnowledgeDocument::getId, docIds));

        knowledgeChunkMapper.update(null, new LambdaUpdateWrapper<KnowledgeChunk>()
                .set(KnowledgeChunk::getKnowledgeBaseId, target.getId())
                .in(KnowledgeChunk::getDocumentId, docIds));

        // 5) 删除这些文档在 Milvus 中的旧向量（后续异步重新索引会按新 knowledge_base_id 重建）
        for (Long docId : docIds) {
            try {
                milvusVectorStore.deleteByDocumentId(String.valueOf(docId));
            } catch (Exception e) {
                log.warn("迁移时删除 Milvus 向量失败: docId={}", docId, e);
            }
        }

        // 6) 调整 docCount / chunkCount
        // 源：减去
        int newSourceDocCount = Math.max(0, (source.getDocCount() == null ? 0 : source.getDocCount()) - toMigrate.size());
        int newSourceChunkCount = Math.max(0, (source.getChunkCount() == null ? 0 : source.getChunkCount()) - docSumChunkCount);
        source.setDocCount(newSourceDocCount);
        source.setChunkCount(newSourceChunkCount);
        knowledgeBaseMapper.updateById(source);

        // 目标：加上
        int newTargetDocCount = (target.getDocCount() == null ? 0 : target.getDocCount()) + toMigrate.size();
        int newTargetChunkCount = (target.getChunkCount() == null ? 0 : target.getChunkCount()) + docSumChunkCount;
        target.setDocCount(newTargetDocCount);
        target.setChunkCount(newTargetChunkCount);
        knowledgeBaseMapper.updateById(target);

        // 7) 触发被迁移文档的异步重新索引（状态：pending）。
        // 必须在事务提交后再发消息，否则 consumer 可能读到旧 knowledge_base_id 并 updateById 覆盖。
        for (Long docId : docIds) {
            try {
                knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                        .set(KnowledgeDocument::getStatus, "pending")
                        .set(KnowledgeDocument::getErrorMessage, null)
                        .eq(KnowledgeDocument::getId, docId));
            } catch (Exception e) {
                log.warn("迁移时重置文档状态失败: docId={}", docId, e);
            }
        }
        final List<Long> finalDocIds = java.util.Collections.unmodifiableList(docIds);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (Long docId : finalDocIds) {
                            try {
                                rabbitTemplate.convertAndSend("knowledge.exchange", "knowledge.document.process", docId);
                            } catch (Exception e) {
                                log.warn("迁移后重新入队索引失败: docId={}", docId, e);
                            }
                        }
                    }
                }
        );

        log.info("知识库文档迁移完成: source={}, target={}, docs={}, chunks={}, reason={}",
                sourceId, target.getId(), toMigrate.size(), chunkCount, dto.getReason());

        return new KnowledgeMigrateResultVO(toMigrate.size(), chunkCount, sourceId, target.getId());
    }

    @Override
    public List<KnowledgeBaseVO> listAll() {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getStatus, "active");
        wrapper.orderByDesc(KnowledgeBase::getCreatedAt);
        return knowledgeBaseMapper.selectList(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    private KnowledgeBase findOrThrow(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        String creatorName = null;
        if (kb.getCreatorId() != null) {
            SysUser user = sysUserMapper.selectById(kb.getCreatorId());
            if (user != null) {
                creatorName = user.getRealName();
            }
        }
        String projectName = null;
        if (kb.getProjectId() != null) {
            Project project = projectMapper.selectById(kb.getProjectId());
            if (project != null) {
                projectName = project.getName();
            }
        }
        return KnowledgeBaseVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .projectId(kb.getProjectId())
                .projectName(projectName)
                .creatorId(kb.getCreatorId())
                .creatorName(creatorName)
                .docCount(kb.getDocCount())
                .chunkCount(kb.getChunkCount())
                .status(kb.getStatus())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }
}
