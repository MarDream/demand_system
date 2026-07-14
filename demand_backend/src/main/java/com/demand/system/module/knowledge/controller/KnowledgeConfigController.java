package com.demand.system.module.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.result.Result;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.config.MilvusConfig;
import com.demand.system.module.knowledge.dto.KnowledgeConfigVO;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库配置聚合接口。
 * 前端初始化时调用一次，获取分块参数、模型状态、候选列表。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeConfigController {

    private final KnowledgeConfig knowledgeConfig;
    private final MilvusConfig milvusConfig;
    private final LlmModelMapper llmModelMapper;
    private final LlmProviderMapper llmProviderMapper;
    private final LlmModelResolver llmModelResolver;

    public KnowledgeConfigController(KnowledgeConfig knowledgeConfig,
                                     MilvusConfig milvusConfig,
                                     LlmModelMapper llmModelMapper,
                                     LlmProviderMapper llmProviderMapper,
                                     LlmModelResolver llmModelResolver) {
        this.knowledgeConfig = knowledgeConfig;
        this.milvusConfig = milvusConfig;
        this.llmModelMapper = llmModelMapper;
        this.llmProviderMapper = llmProviderMapper;
        this.llmModelResolver = llmModelResolver;
    }

    /**
     * 获取知识库配置状态（所有登录用户可用）
     */
    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public Result<KnowledgeConfigVO> getConfig() {
        KnowledgeConfigVO vo = new KnowledgeConfigVO();

        // 分块参数
        vo.setChunkSize(knowledgeConfig.getChunkSize());
        vo.setChunkOverlap(knowledgeConfig.getChunkOverlap());
        vo.setSearchTopK(knowledgeConfig.getSearchTopK());

        // Milvus 维度
        vo.setMilvusDimension(milvusConfig.getDimension());

        // Embedding 状态
        vo.setEmbedding(buildModelStatus(LlmApplicationCode.KNOWLEDGE_EMBEDDING, milvusConfig.getDimension()));

        // Reranker 状态
        vo.setReranker(buildModelStatus(LlmApplicationCode.KNOWLEDGE_RERANK, null));

        // Chat 状态
        vo.setChat(buildChatModelStatus());

        // 候选列表
        vo.setEmbeddingCandidates(buildCandidateList("embedding"));
        vo.setRerankerCandidates(buildCandidateList("rerank"));
        vo.setChatCandidates(buildChatCandidateList());

        return Result.success(vo);
    }

    /**
     * 更新知识库分块参数（admin 权限）
     */
    @PutMapping("/config")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<KnowledgeConfigVO> updateConfig(@RequestBody Map<String, Integer> params) {
        if (params.containsKey("chunkSize")) {
            knowledgeConfig.setChunkSize(params.get("chunkSize"));
        }
        if (params.containsKey("chunkOverlap")) {
            knowledgeConfig.setChunkOverlap(params.get("chunkOverlap"));
        }
        if (params.containsKey("searchTopK")) {
            knowledgeConfig.setSearchTopK(params.get("searchTopK"));
        }
        return getConfig();
    }

    // ==================== 私有方法 ====================

    private KnowledgeConfigVO.ModelStatus buildModelStatus(String applicationCode, Integer milvusDimension) {
        // 状态展示与实际业务调用保持一致：优先使用功能点绑定模型，再回退全局默认模型。
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(applicationCode);
        LlmModel defaultModel = resolved != null ? resolved.model() : null;

        if (defaultModel == null) {
            return KnowledgeConfigVO.ModelStatus.notConfigured();
        }

        LlmProvider provider = llmProviderMapper.selectById(defaultModel.getProviderId());

        KnowledgeConfigVO.ModelStatus status = new KnowledgeConfigVO.ModelStatus();
        status.setConfigured(true);
        status.setModelId(defaultModel.getModelId());
        status.setName(defaultModel.getName());
        status.setProviderName(provider != null ? provider.getName() : null);
        status.setDimension(defaultModel.getDimension());
        status.setTestSuccess(defaultModel.getTestSuccess());
        status.setTestError(defaultModel.getTestError());

        // 维度匹配检查（仅 embedding）
        if ("embedding".equalsIgnoreCase(defaultModel.getModelType()) && milvusDimension != null && defaultModel.getDimension() != null) {
            status.setDimensionMatch(defaultModel.getDimension() == milvusDimension);
        }

        return status;
    }

    private KnowledgeConfigVO.ModelStatus buildChatModelStatus() {
        // 知识库问答使用独立的功能点模型配置。
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(LlmApplicationCode.KNOWLEDGE_ANSWER);
        LlmModel defaultModel = resolved != null ? resolved.model() : null;

        if (defaultModel == null) {
            return KnowledgeConfigVO.ModelStatus.notConfigured();
        }

        LlmProvider provider = llmProviderMapper.selectById(defaultModel.getProviderId());

        KnowledgeConfigVO.ModelStatus status = new KnowledgeConfigVO.ModelStatus();
        status.setConfigured(true);
        status.setModelId(defaultModel.getModelId());
        status.setName(defaultModel.getName());
        status.setProviderName(provider != null ? provider.getName() : null);
        status.setTestSuccess(defaultModel.getTestSuccess());
        status.setTestError(defaultModel.getTestError());
        return status;
    }

    private List<KnowledgeConfigVO.ModelCandidate> buildCandidateList(String modelType) {
        List<LlmModel> models = llmModelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getModelType, modelType)
                        .eq(LlmModel::getEnabled, true)
                        .orderByDesc(LlmModel::getIsDefault)
                        .orderByAsc(LlmModel::getId)
        );

        List<KnowledgeConfigVO.ModelCandidate> candidates = new ArrayList<>();
        for (LlmModel model : models) {
            LlmProvider provider = llmProviderMapper.selectById(model.getProviderId());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) continue;

            KnowledgeConfigVO.ModelCandidate c = new KnowledgeConfigVO.ModelCandidate();
            c.setId(model.getId());
            c.setProviderId(provider.getId());
            c.setProviderName(provider.getName());
            c.setName(model.getName());
            c.setModelId(model.getModelId());
            c.setModelType(model.getModelType());
            c.setDimension(model.getDimension());
            c.setIsDefault(model.getIsDefault());
            c.setEnabled(model.getEnabled());
            candidates.add(c);
        }
        return candidates;
    }

    private List<KnowledgeConfigVO.ModelCandidate> buildChatCandidateList() {
        List<LlmModel> models = llmModelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getEnabled, true)
                        .notIn(LlmModel::getModelType, "embedding", "rerank")
                        .orderByDesc(LlmModel::getIsDefault)
                        .orderByAsc(LlmModel::getId)
        );

        List<KnowledgeConfigVO.ModelCandidate> candidates = new ArrayList<>();
        for (LlmModel model : models) {
            LlmProvider provider = llmProviderMapper.selectById(model.getProviderId());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) continue;

            KnowledgeConfigVO.ModelCandidate c = new KnowledgeConfigVO.ModelCandidate();
            c.setId(model.getId());
            c.setProviderId(provider.getId());
            c.setProviderName(provider.getName());
            c.setName(model.getName());
            c.setModelId(model.getModelId());
            c.setModelType(model.getModelType());
            c.setDimension(model.getDimension());
            c.setIsDefault(model.getIsDefault());
            c.setEnabled(model.getEnabled());
            candidates.add(c);
        }
        return candidates;
    }
}
