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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlmProviderServiceImpl implements LlmProviderService {

    private final LlmProviderMapper providerMapper;
    private final LlmModelMapper modelMapper;
    private final LlmGateway llmGateway;

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
        model.setTemperature(dto.getTemperature());
        model.setMaxTokens(dto.getMaxTokens());
        model.setIsDefault(dto.getIsDefault());
        model.setEnabled(dto.getEnabled());
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
    public LlmTestResultVO testModel(Long modelId, LlmTestRequestDTO request) {
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null) throw new RuntimeException("模型不存在");

        LlmProvider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null) throw new RuntimeException("接入组不存在");

        LlmGatewayConfig.Provider gwProvider = new LlmGatewayConfig.Provider();
        gwProvider.setProtocol(provider.getProtocol());
        gwProvider.setBaseUrl(provider.getBaseUrl());
        gwProvider.setApiKey(provider.getApiKey());
        gwProvider.setModel(model.getModelId());

        String systemPrompt = (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank())
                ? request.getSystemPrompt() : "You are a helpful assistant.";

        long start = System.currentTimeMillis();
        LlmTestResultVO result;
        try {
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

            // 记录测试结果
            updateTestResult(modelId, true, (int) chatResult.getDurationMs(), null);
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

    // ==================== Sniff ====================

    @Override
    public List<SniffedModelVO> sniffModels(Long id) {
        LlmProvider provider = providerMapper.selectById(id);
        if (provider == null) throw new RuntimeException("接入组不存在");

        LlmGatewayConfig.Provider gwProvider = new LlmGatewayConfig.Provider();
        gwProvider.setProtocol(provider.getProtocol());
        gwProvider.setBaseUrl(provider.getBaseUrl());
        gwProvider.setApiKey(provider.getApiKey());

        List<LlmGateway.ModelInfo> remoteModels = llmGateway.fetchModelList(gwProvider);

        Set<String> existingModelIds = modelMapper.selectList(
                new LambdaQueryWrapper<LlmModel>().eq(LlmModel::getProviderId, id)
        ).stream().map(LlmModel::getModelId).collect(Collectors.toSet());

        return remoteModels.stream().map(m -> {
            SniffedModelVO vo = new SniffedModelVO();
            vo.setModelId(m.getId());
            vo.setOwnedBy(m.getOwnedBy());
            vo.setAlreadyExists(existingModelIds.contains(m.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== Private ====================

    private void clearTypeDefaults(Long providerId, String modelType) {
        modelMapper.update(null,
                new LambdaUpdateWrapper<LlmModel>()
                        .eq(LlmModel::getProviderId, providerId)
                        .eq(LlmModel::getModelType, modelType)
                        .eq(LlmModel::getIsDefault, true)
                        .set(LlmModel::getIsDefault, false)
        );
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
