package com.demand.system.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.config.MilvusConfig;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final LlmGateway llmGateway;
    private final LlmModelMapper llmModelMapper;
    private final LlmProviderMapper llmProviderMapper;
    private final MilvusConfig milvusConfig;
    private final MilvusVectorStore milvusVectorStore;

    /** 缓存最近一次解析到的 embedding 模型维度，供 Milvus 集合维度检查使用 */
    private volatile int cachedEmbeddingDimension = -1;

    public EmbeddingServiceImpl(LlmGateway llmGateway,
                                LlmModelMapper llmModelMapper,
                                LlmProviderMapper llmProviderMapper,
                                MilvusConfig milvusConfig,
                                MilvusVectorStore milvusVectorStore) {
        this.llmGateway = llmGateway;
        this.llmModelMapper = llmModelMapper;
        this.llmProviderMapper = llmProviderMapper;
        this.milvusConfig = milvusConfig;
        this.milvusVectorStore = milvusVectorStore;
    }

    // ==================== Embedding with fallback ====================

    @Override
    public List<float[]> embed(List<String> texts) {
        return invokeWithFallback("embedding", "Embedding", providers -> {
            // 默认模型在列表首位，逐一尝试
            for (LlmGatewayConfig.Provider provider : providers) {
                try {
                    List<float[]> result = llmGateway.embedWithProvider(provider, texts);
                    log.info("Embedding 调用成功: model={}", provider.getModel());
                    return result;
                } catch (Exception e) {
                    log.warn("Embedding 调用失败: model={}, error={}", provider.getModel(), e.getMessage());
                    // 继续尝试下一个兜底模型
                }
            }
            // 所有模型都失败
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所有 Embedding 模型均调用失败，请检查模型配置。");
        });
    }

    @Override
    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    // ==================== Reranker with fallback ====================

    @Override
    public List<Double> rerank(String query, List<String> documents) {
        return invokeWithFallback("rerank", "Reranker", providers -> {
            for (LlmGatewayConfig.Provider provider : providers) {
                try {
                    List<Double> result = llmGateway.rerankWithProvider(provider, query, documents);
                    log.info("Reranker 调用成功: model={}", provider.getModel());
                    return result;
                } catch (Exception e) {
                    log.warn("Reranker 调用失败: model={}, error={}", provider.getModel(), e.getMessage());
                    // 继续尝试下一个兜底模型
                }
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所有 Reranker 模型均调用失败，请检查模型配置。");
        });
    }

    // ==================== 模型配置 ====================

    @Override
    public EmbeddingModelConfig getDefaultModelConfig() {
        // 查默认 embedding 模型
        LlmModel defaultModel = llmModelMapper.selectOne(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getModelType, "embedding")
                        .eq(LlmModel::getIsDefault, true)
                        .eq(LlmModel::getEnabled, true)
                        .last("LIMIT 1")
        );

        if (defaultModel == null) {
            defaultModel = llmModelMapper.selectOne(
                    new LambdaQueryWrapper<LlmModel>()
                            .eq(LlmModel::getModelType, "embedding")
                            .eq(LlmModel::getEnabled, true)
                            .last("LIMIT 1")
            );
        }

        if (defaultModel == null) {
            return null;
        }

        // 如果模型没有配置这三个参数，返回 null 让调用方使用全局默认值
        if (defaultModel.getChunkSize() == null && defaultModel.getChunkOverlap() == null && defaultModel.getSearchTopK() == null) {
            return null;
        }

        return new EmbeddingModelConfig(
                defaultModel.getChunkSize() != null ? defaultModel.getChunkSize() : 512,
                defaultModel.getChunkOverlap() != null ? defaultModel.getChunkOverlap() : 128,
                defaultModel.getSearchTopK() != null ? defaultModel.getSearchTopK() : 20
        );
    }

    // ==================== 动态模型解析（默认 + 兜底列表） ====================

    /**
     * 通用调用入口：解析该类型下所有可用的 Provider 列表（默认在前，兜底在后），
     * 然后交给调用器逐一尝试。
     */
    private <T> T invokeWithFallback(String modelType, String label, FallbackInvoker<T> invoker) {
        List<LlmGatewayConfig.Provider> providers = resolveAllProviders(modelType, label);
        return invoker.invoke(providers);
    }

    @FunctionalInterface
    private interface FallbackInvoker<T> {
        T invoke(List<LlmGatewayConfig.Provider> providers);
    }

    /**
     * 从数据库查找指定类型的所有可用 Provider 列表。
     * 排序规则：
     *   1. isDefault=true 且 enabled=true 的模型排在最前（默认模型）
     *   2. 其余 enabled=true 的模型作为兜底，按 id 升序排列
     * 同时校验接入组存在且已启用。
     */
    private List<LlmGatewayConfig.Provider> resolveAllProviders(String modelType, String label) {
        // 1. 查出该类型下所有启用的模型，默认排前面
        List<LlmModel> models = llmModelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getModelType, modelType)
                        .eq(LlmModel::getEnabled, true)
                        .orderByDesc(LlmModel::getIsDefault)
                        .orderByAsc(LlmModel::getId)
        );

        if (models.isEmpty()) {
            throw missingModelConfig(modelType, label);
        }

        // 2. 逐个校验接入组，构建 Provider 列表
        List<LlmGatewayConfig.Provider> providers = new ArrayList<>();
        List<String> skippedReasons = new ArrayList<>();

        for (LlmModel model : models) {
            LlmProvider provider = llmProviderMapper.selectById(model.getProviderId());
            if (provider == null) {
                skippedReasons.add(String.format("模型[%s]接入组不存在", model.getModelId()));
                continue;
            }
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                skippedReasons.add(String.format("模型[%s]接入组[%s]未启用", model.getModelId(), provider.getName()));
                continue;
            }

            LlmGatewayConfig.Provider gwProvider = new LlmGatewayConfig.Provider();
            gwProvider.setProtocol(provider.getProtocol());
            gwProvider.setBaseUrl(provider.getBaseUrl());
            gwProvider.setApiKey(provider.getApiKey());
            gwProvider.setModel(model.getModelId());
            if (model.getDimension() != null) {
                gwProvider.setDimension(String.valueOf(model.getDimension()));
            }
            providers.add(gwProvider);

            // 缓存默认 embedding 模型的维度
            if ("embedding".equals(modelType) && Boolean.TRUE.equals(model.getIsDefault()) && model.getDimension() != null) {
                cachedEmbeddingDimension = model.getDimension();
                checkDimensionCompatibility(model.getDimension());
            }
        }

        if (providers.isEmpty()) {
            String detail = skippedReasons.isEmpty() ? "" : "（" + String.join("；", skippedReasons) + "）";
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    String.format("请先配置可用的%s。%s", label, detail)
            );
        }

        if (!skippedReasons.isEmpty()) {
            log.warn("{}: 以下模型因配置问题被跳过: {}", label, String.join("；", skippedReasons));
        }

        log.info("{}: 可用模型列表(默认优先): {}", label, providers.stream().map(LlmGatewayConfig.Provider::getModel).toList());
        return providers;
    }

    private BusinessException missingModelConfig(String modelType, String label) {
        String modelTypeLabel = "embedding".equals(modelType) ? "Embedding 向量模型" : "Reranker 重排模型";
        String message = String.format("请先配置可用的%s。", modelTypeLabel);
        log.warn("{}: 数据库中未找到 modelType={} 的启用模型", label, modelType);
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 检查数据库中 embedding 模型的维度与 Milvus 集合维度是否匹配。
     * 不匹配时输出警告日志，提示管理员重建集合。
     */
    private void checkDimensionCompatibility(int modelDimension) {
        int milvusDimension = milvusConfig.getDimension();
        if (modelDimension != milvusDimension) {
            log.warn("⚠️ Embedding模型维度({})与Milvus集合维度({})不一致！需要重建Milvus集合或切换回匹配的模型。"
                     + "可通过API /api/v1/knowledge/milvus/rebuild?dimension={} 重建集合（会清除所有向量数据）",
                    modelDimension, milvusDimension, modelDimension);
        }
    }

    /**
     * 获取当前 embedding 模型配置的维度。
     * 优先返回数据库缓存维度，否则返回 Milvus 配置维度。
     */
    public int getEmbeddingDimension() {
        return cachedEmbeddingDimension > 0 ? cachedEmbeddingDimension : milvusConfig.getDimension();
    }
}
