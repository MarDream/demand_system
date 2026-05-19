package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.auth.entity.SysUser;
import com.demand.system.module.auth.mapper.SysUserMapper;
import com.demand.system.module.knowledge.dto.KnowledgeBaseCreateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseUpdateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseVO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper,
                                   KnowledgeChunkMapper knowledgeChunkMapper,
                                   KnowledgeDocumentMapper knowledgeDocumentMapper,
                                   MinioStorageService minioStorageService,
                                   MilvusVectorStore milvusVectorStore,
                                   SysUserMapper sysUserMapper,
                                   ProjectMapper projectMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.minioStorageService = minioStorageService;
        this.milvusVectorStore = milvusVectorStore;
        this.sysUserMapper = sysUserMapper;
        this.projectMapper = projectMapper;
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
