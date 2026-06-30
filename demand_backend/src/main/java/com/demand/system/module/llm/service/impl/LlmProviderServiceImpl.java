package com.demand.system.module.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.constant.LlmModelRole;
import com.demand.system.module.llm.dto.*;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import com.demand.system.module.llm.service.LlmProviderService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LlmProviderServiceImpl implements LlmProviderService {
    private final LlmProviderMapper providerMapper;
    private final LlmModelMapper modelMapper;
    private final LlmGateway llmGateway;

    public LlmProviderServiceImpl(LlmProviderMapper providerMapper,
                                  LlmModelMapper modelMapper,
                                  LlmGateway llmGateway) {
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
        this.llmGateway = llmGateway;
    }

    // ==================== Provider ====================

    @Override
    public List<LlmProviderVO> list() {
        List<LlmProvider> providers = providerMapper.selectList(null);
        return providers.stream().map(this::toProviderVO).collect(Collectors.toList());
    }

    @Override
    public LlmProviderVO getById(Long id) {
        return toProviderVO(providerMapper.selectById(id));
    }

    @Override
    @Transactional
    public LlmProviderVO create(LlmProviderDTO dto) {
        LlmProvider entity = new LlmProvider();
        BeanUtils.copyProperties(dto, entity);
        providerMapper.insert(entity);
        return toProviderVO(entity);
    }

    @Override
    @Transactional
    public LlmProviderVO update(Long id, LlmProviderDTO dto) {
        LlmProvider entity = providerMapper.selectById(id);
        if (entity == null) throw new RuntimeException("配置不存在");

        entity.setName(dto.getName());
        entity.setProtocol(dto.getProtocol());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setEnabled(dto.getEnabled());

        if (dto.getApiKey() != null && !dto.getApiKey().isEmpty() && !dto.getApiKey().contains("****")) {
            entity.setApiKey(dto.getApiKey());
        }

        providerMapper.updateById(entity);
        return toProviderVO(entity);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        modelMapper.delete(new LambdaQueryWrapper<LlmModel>().eq(LlmModel::getProviderId, id));
        providerMapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(Long id) {
        LlmProvider entity = providerMapper.selectById(id);
        if (entity == null) throw new RuntimeException("配置不存在");
        entity.setEnabled(!entity.getEnabled());
        providerMapper.updateById(entity);
    }

    @Override
    public String getApiKey(Long id) {
        LlmProvider entity = providerMapper.selectById(id);
        if (entity == null) throw new RuntimeException("配置不存在");
        return entity.getApiKey();
    }

    // ==================== Model ====================

    @Override
    @Transactional
    public LlmModelVO addModel(Long providerId, LlmModelDTO dto) {
        LlmProvider provider = providerMapper.selectById(providerId);
        if (provider == null) throw new RuntimeException("接入组不存在");

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearTypeDefaults(providerId, dto.getModelType());
        }

        LlmModel model = new LlmModel();
        BeanUtils.copyProperties(dto, model);
        model.setProviderId(providerId);
        modelMapper.insert(model);
        return toModelVO(model);
    }

    @Override
    @Transactional
    public LlmModelVO updateModel(Long modelId, LlmModelDTO dto) {
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null) throw new RuntimeException("模型不存在");

        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearTypeDefaults(model.getProviderId(), dto.getModelType());
        }

        model.setName(dto.getName());
        model.setModelId(dto.getModelId());
        model.setModelType(dto.getModelType());
        model.setDimension(dto.getDimension());
        model.setTemperature(dto.getTemperature());
        model.setMaxTokens(dto.getMaxTokens());
        model.setIsDefault(dto.getIsDefault());
        model.setEnabled(dto.getEnabled());
        model.setChunkSize(dto.getChunkSize());
        model.setChunkOverlap(dto.getChunkOverlap());
        model.setSearchTopK(dto.getSearchTopK());
        modelMapper.updateById(model);
        return toModelVO(model);
    }

    @Override
    public void deleteModel(Long modelId) {
        modelMapper.deleteById(modelId);
    }

    @Override
    public void toggleModelEnabled(Long modelId) {
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null) throw new RuntimeException("模型不存在");
        model.setEnabled(!model.getEnabled());
        modelMapper.updateById(model);
    }

    @Override
    @Transactional
    public void toggleModelDefault(Long modelId) {
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null) throw new RuntimeException("模型不存在");

        if (Boolean.TRUE.equals(model.getIsDefault())) {
            // 取消默认
            model.setIsDefault(false);
            modelMapper.updateById(model);
        } else {
            // 设为默认：先清理同类默认，再设置当前
            clearTypeDefaults(model.getProviderId(), model.getModelType());
            model.setIsDefault(true);
            modelMapper.updateById(model);
        }
    }

    @Override
    @Transactional
    public LlmTestResultVO testModel(Long modelId, LlmTestRequestDTO request) {
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null) throw new RuntimeException("模型不存在");

        LlmProvider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) throw new RuntimeException("接入组不存在");

        LlmGatewayConfig.Provider gwProvider = buildGatewayProvider(provider, model);

        String systemPrompt = (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank())
                ? request.getSystemPrompt() : "You are a helpful assistant.";

        long start = System.currentTimeMillis();
        LlmTestResultVO result;
        try {
            if (isModelType(model, "embedding")) {
                List<float[]> vectors = llmGateway.embedWithProvider(gwProvider, List.of(request.getUserMessage()));
                long duration = System.currentTimeMillis() - start;
                int dimension = vectors.isEmpty() || vectors.get(0) == null ? 0 : vectors.get(0).length;
                result = LlmTestResultVO.builder()
                        .success(true)
                        .content("Embedding 调用成功，返回向量维度：" + dimension)
                        .durationMs(duration)
                        .model(model.getModelId())
                        .build();
                updateTestResult(modelId, true, (int) duration, null);
            } else if (isModelType(model, "rerank")) {
                List<Double> scores = llmGateway.rerankWithProvider(
                        gwProvider,
                        request.getUserMessage(),
                        List.of(request.getUserMessage(), "这是用于连通性测试的候选文档")
                );
                long duration = System.currentTimeMillis() - start;
                result = LlmTestResultVO.builder()
                        .success(true)
                        .content("Rerank 调用成功，返回相关性分数：" + scores)
                        .durationMs(duration)
                        .model(model.getModelId())
                        .build();
                updateTestResult(modelId, true, (int) duration, null);
            } else {
                LlmGateway.ChatResult chatResult = llmGateway.chatWithProvider(gwProvider, systemPrompt, request.getUserMessage());
                result = LlmTestResultVO.builder()
                        .success(true)
                        .content(chatResult.getContent())
                        .durationMs(chatResult.getDurationMs())
                        .promptTokens(chatResult.getPromptTokens())
                        .completionTokens(chatResult.getCompletionTokens())
                        .totalTokens(chatResult.getTotalTokens())
                        .model(chatResult.getModel())
                        .build();
                updateTestResult(modelId, true, (int) chatResult.getDurationMs(), null);
            }
        } catch (Exception e) {
            result = LlmTestResultVO.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();

            long duration = System.currentTimeMillis() - start;
            updateTestResult(modelId, false, (int) duration, e.getMessage());
        }

        return result;
    }

    @Override
    public LlmModelVO getModelByType(String type) {
        LlmModel model = modelMapper.selectOne(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getModelType, type)
                        .eq(LlmModel::getIsDefault, true)
                        .eq(LlmModel::getEnabled, true)
                        .last("LIMIT 1")
        );
        if (model == null) {
            // fallback: 取该类型下第一个启用的模型
            model = modelMapper.selectOne(
                    new LambdaQueryWrapper<LlmModel>()
                            .eq(LlmModel::getModelType, type)
                            .eq(LlmModel::getEnabled, true)
                            .last("LIMIT 1")
            );
        }
        if (model == null) throw new RuntimeException("类型 [" + type + "] 下没有可用的模型配置");
        return toModelVO(model);
    }

    // ==================== Chat Models (for RAG) ====================

    @Override
    public List<Map<String, Object>> listChatModels() {
        // 查出所有已启用的 provider
        List<LlmProvider> enabledProviders = providerMapper.selectList(
                new LambdaQueryWrapper<LlmProvider>().eq(LlmProvider::getEnabled, true)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (LlmProvider provider : enabledProviders) {
            // 查出该 provider 下已启用且非 embedding/rerank 的模型
            List<LlmModel> chatModels = modelMapper.selectList(
                    new LambdaQueryWrapper<LlmModel>()
                            .eq(LlmModel::getProviderId, provider.getId())
                            .eq(LlmModel::getEnabled, true)
                            .notIn(LlmModel::getModelType, "embedding", "rerank")
                            .orderByDesc(LlmModel::getIsDefault)
            );

            for (LlmModel model : chatModels) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", model.getId());
                item.put("providerId", provider.getId());
                item.put("providerName", provider.getName());
                item.put("name", model.getName());
                item.put("modelId", model.getModelId());
                item.put("modelType", model.getModelType());
                item.put("isDefault", model.getIsDefault());
                result.add(item);
            }
        }
        return result;
    }

    // ==================== Sniff ====================

    @Override
    public List<SniffedModelVO> sniffModels(Long id) {
        LlmProvider provider = providerMapper.selectById(id);
        if (provider == null) throw new RuntimeException("接入组不存在");

        LlmGatewayConfig.Provider gwProvider = buildGatewayProvider(provider, null);

        List<LlmGateway.ModelInfo> remoteModels = llmGateway.fetchModelList(gwProvider);

        Set<String> existingModelIds = modelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>().eq(LlmModel::getProviderId, id)
        ).stream().map(LlmModel::getModelId).collect(Collectors.toSet());

        return remoteModels.stream().map(m -> {
            SniffedModelVO vo = new SniffedModelVO();
            vo.setModelId(m.getId());
            vo.setOwnedBy(m.getOwnedBy());
            vo.setContextWindow(m.getContextWindow());
            vo.setCreated(m.getCreated());
            vo.setAlreadyExists(existingModelIds.contains(m.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== Private ====================

    /**
     * 清理同类模型的默认标记。
     * embedding / rerank 全局唯一默认（跨接入组），其他类型按接入组内唯一。
     */
    private void clearTypeDefaults(Long providerId, String modelType) {
        LambdaUpdateWrapper<LlmModel> wrapper = new LambdaUpdateWrapper<LlmModel>()
                .eq(LlmModel::getModelType, modelType)
                .eq(LlmModel::getIsDefault, true)
                .set(LlmModel::getIsDefault, false);

        // embedding 和 rerank 全局唯一默认，不限定 providerId
        if (!"embedding".equalsIgnoreCase(modelType) && !"rerank".equalsIgnoreCase(modelType)) {
            wrapper.eq(LlmModel::getProviderId, providerId);
        }

        modelMapper.update(null, wrapper);
    }

    private LlmGatewayConfig.Provider buildGatewayProvider(LlmProvider provider, LlmModel model) {
        LlmGatewayConfig.Provider gwProvider = new LlmGatewayConfig.Provider();
        gwProvider.setProtocol(provider.getProtocol());
        gwProvider.setBaseUrl(provider.getBaseUrl());
        gwProvider.setApiKey(provider.getApiKey());
        if (model != null) {
            gwProvider.setModel(model.getModelId());
            if (model.getDimension() != null) {
                gwProvider.setDimension(String.valueOf(model.getDimension()));
            }
        }
        return gwProvider;
    }

    private boolean isModelType(LlmModel model, String modelType) {
        String configuredType = model.getModelType();
        if (configuredType != null && configuredType.equalsIgnoreCase(modelType)) {
            return true;
        }
        if (configuredType != null && !configuredType.isBlank() && !"general".equalsIgnoreCase(configuredType)) {
            return false;
        }
        return inferModelType(model.getModelId()).equalsIgnoreCase(modelType);
    }

    private String inferModelType(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return "general";
        }
        String id = modelId.toLowerCase(Locale.ROOT);
        if (id.contains("rerank") || id.contains("reranker")) {
            return "rerank";
        }
        if (id.contains("embedding") || id.contains("embed") || id.contains("text-embedding")
                || id.contains("bge") || id.contains("m3e") || id.contains("gte") || id.contains("e5")) {
            return "embedding";
        }
        return "general";
    }

    private void updateTestResult(Long modelId, boolean success, int duration, String error) {
        LlmModel update = new LlmModel();
        update.setId(modelId);
        update.setTestSuccess(success);
        update.setTestDuration(duration);
        update.setTestError(error);
        update.setTestAt(LocalDateTime.now());
        modelMapper.updateById(update);
    }

    private LlmProviderVO toProviderVO(LlmProvider entity) {
        if (entity == null) return null;
        LlmProviderVO vo = new LlmProviderVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setMaskedApiKey(maskKey(entity.getApiKey()));

        List<LlmModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>().eq(LlmModel::getProviderId, entity.getId())
        );
        vo.setModels(models.stream().map(this::toModelVO).collect(Collectors.toList()));
        return vo;
    }

    private LlmModelVO toModelVO(LlmModel entity) {
        if (entity == null) return null;
        LlmModelVO vo = new LlmModelVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }
}
