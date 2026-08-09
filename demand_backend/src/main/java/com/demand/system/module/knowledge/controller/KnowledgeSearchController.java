package com.demand.system.module.knowledge.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.config.MilvusConfig;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.KnowledgeSearchService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeSearchController {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchController.class);
    private final KnowledgeSearchService searchService;
    private final MilvusVectorStore milvusVectorStore;
    private final MilvusConfig milvusConfig;
    private final KnowledgeConfig knowledgeConfig;
    private final EmbeddingService embeddingService;
    private final LlmModelMapper llmModelMapper;
    private final LlmProviderMapper llmProviderMapper;

    public KnowledgeSearchController(KnowledgeSearchService searchService,
                                     MilvusVectorStore milvusVectorStore,
                                     MilvusConfig milvusConfig,
                                     KnowledgeConfig knowledgeConfig,
                                     EmbeddingService embeddingService,
                                     LlmModelMapper llmModelMapper,
                                     LlmProviderMapper llmProviderMapper) {
        this.searchService = searchService;
        this.milvusVectorStore = milvusVectorStore;
        this.milvusConfig = milvusConfig;
        this.knowledgeConfig = knowledgeConfig;
        this.embeddingService = embeddingService;
        this.llmModelMapper = llmModelMapper;
        this.llmProviderMapper = llmProviderMapper;
    }

    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public Result<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        request.setRequesterId(SecurityUtils.getCurrentUserId());
        try {
            KnowledgeSearchResponse response = searchService.search(request);
            return Result.success(response);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw wrapLlmException(e);
        }
    }

    @PostMapping(value = "/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter streamSearch(@Valid @RequestBody KnowledgeSearchRequest request) {
        request.setRequesterId(SecurityUtils.getCurrentUserId());
        return searchService.streamSearch(request);
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("module", "knowledge-rag");
        stats.put("status", "active");
        stats.put("milvusDimension", milvusConfig.getDimension());
        return Result.success(stats);
    }

    private LlmModel findDefaultModel(String modelType) {
        // 优先取 isDefault + enabled
        LlmModel model = llmModelMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getModelType, modelType)
                        .eq(LlmModel::getIsDefault, true)
                        .eq(LlmModel::getEnabled, true)
                        .last("LIMIT 1")
        );
        if (model == null) {
            // fallback: 该类型下第一个 enabled
            model = llmModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LlmModel>()
                            .eq(LlmModel::getModelType, modelType)
                            .eq(LlmModel::getEnabled, true)
                            .last("LIMIT 1")
            );
        }
        return model;
    }

    private List<Map<String, Object>> listCandidates(String modelType) {
        List<LlmModel> models = llmModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getModelType, modelType)
                        .eq(LlmModel::getEnabled, true)
                        .orderByDesc(LlmModel::getIsDefault)
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (LlmModel m : models) {
            LlmProvider p = llmProviderMapper.selectById(m.getProviderId());
            if (p == null || !Boolean.TRUE.equals(p.getEnabled())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("modelId", m.getModelId());
            item.put("name", m.getName());
            item.put("providerId", p.getId());
            item.put("providerName", p.getName());
            item.put("isDefault", m.getIsDefault());
            item.put("dimension", m.getDimension());
            item.put("testSuccess", m.getTestSuccess());
            result.add(item);
        }
        return result;
    }

    /**
     * 重建 Milvus 集合（清除所有向量数据，用新维度重建）。
     * 当 Embedding 模型维度变化时需要调用此接口。
     */
    @PostMapping("/milvus/rebuild")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    public Result<Map<String, Object>> rebuildMilvus(@RequestParam(defaultValue = "0") int dimension) {
        int targetDimension = dimension > 0 ? dimension : milvusConfig.getDimension();
        boolean success = milvusVectorStore.rebuildCollection(targetDimension);
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("dimension", targetDimension);
            result.put("message", "集合重建成功，新维度=" + targetDimension + "。请重新导入知识库文档。");
            return Result.success(result);
        }
        return Result.fail(ErrorCode.INTERNAL_ERROR, "集合重建失败，请查看后端日志");
    }

    /**
     * 将 LLM API 调用异常转换为业务异常，提供用户友好的提示信息，
     * 避免将技术细节（如 429/500 响应体、URL 等）暴露给前端。
     */
    private BusinessException wrapLlmException(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "知识库检索失败，请稍后重试");
        }

        // 余额不足 / 资源包耗尽
        if (msg.contains("余额不足") || msg.contains("无可用资源包")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务余额不足，请联系管理员充值后重试");
        }

        // API 调用失败（4xx/5xx 上游错误）
        if (msg.contains("API调用失败") || msg.contains("LLM API调用失败")) {
            if (msg.contains("429") || msg.contains("Too Many Requests")) {
                return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务请求过于频繁，请稍后重试");
            }
            if (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("Authentication")) {
                return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务认证失败，请检查 API Key 配置");
            }
            if (msg.contains("403") || msg.contains("Forbidden")) {
                return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务访问被拒绝，请检查权限配置");
            }
            if (msg.contains("404") || msg.contains("Not Found")) {
                return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务接口地址无效，请检查模型配置");
            }
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务暂时不可用，请稍后重试");
        }

        // Embedding / Reranker 特定失败
        if (msg.contains("Embedding调用失败")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "文本向量化服务异常，请稍后重试");
        }
        if (msg.contains("Reranker调用失败")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "文本排序服务异常，请稍后重试");
        }
        if (msg.contains("Chat调用失败") || msg.contains("Chat流式调用失败")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 问答服务异常，请稍后重试");
        }

        // 超时
        if (msg.contains("timeout") || msg.contains("超时")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务响应超时，请稍后重试");
        }

        // 连接失败
        if (msg.contains("Connection refused") || msg.contains("连接失败") || msg.contains("Connect timed out")) {
            return new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 服务连接失败，请检查网络或服务地址配置");
        }

        // 兜底
        log.warn("未识别的 LLM 异常类型，原始消息: {}", msg);
        return new BusinessException(ErrorCode.INTERNAL_ERROR, "知识库检索失败，请稍后重试");
    }
}
