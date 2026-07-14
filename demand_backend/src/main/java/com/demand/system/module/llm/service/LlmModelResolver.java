package com.demand.system.module.llm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.llm.entity.LlmApplication;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmApplicationMapper;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一解析各业务功能点使用的 LLM 模型。
 * 优先使用应用配置中明确指定的模型，再回退到该类型的默认模型和其他可用模型。
 */
@Service
public class LlmModelResolver {
    private final LlmApplicationMapper applicationMapper;
    private final LlmModelMapper modelMapper;
    private final LlmProviderMapper providerMapper;

    public LlmModelResolver(LlmApplicationMapper applicationMapper,
                            LlmModelMapper modelMapper,
                            LlmProviderMapper providerMapper) {
        this.applicationMapper = applicationMapper;
        this.modelMapper = modelMapper;
        this.providerMapper = providerMapper;
    }

    public List<ResolvedModel> resolveCandidates(String applicationCode) {
        LlmApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<LlmApplication>().eq(LlmApplication::getCode, applicationCode)
        );
        if (application == null || !Boolean.TRUE.equals(application.getEnabled())) {
            return List.of();
        }

        Set<Long> orderedIds = new LinkedHashSet<>();
        if (application.getModelId() != null) {
            orderedIds.add(application.getModelId());
        }

        LambdaQueryWrapper<LlmModel> query = new LambdaQueryWrapper<LlmModel>()
                .eq(LlmModel::getEnabled, true)
                .orderByDesc(LlmModel::getIsDefault)
                .orderByAsc(LlmModel::getId);
        if (isChat(application.getModelType())) {
            query.notIn(LlmModel::getModelType, "embedding", "rerank");
        } else {
            query.eq(LlmModel::getModelType, application.getModelType());
        }
        modelMapper.selectList(query).forEach(model -> orderedIds.add(model.getId()));

        List<ResolvedModel> result = new ArrayList<>();
        for (Long modelId : orderedIds) {
            LlmModel model = modelMapper.selectById(modelId);
            if (model == null || !Boolean.TRUE.equals(model.getEnabled())
                    || !isCompatible(application.getModelType(), model)) {
                continue;
            }
            LlmProvider provider = providerMapper.selectById(model.getProviderId());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                continue;
            }
            result.add(new ResolvedModel(application, model, provider));
        }
        return result;
    }

    public ResolvedModel resolveFirst(String applicationCode) {
        List<ResolvedModel> candidates = resolveCandidates(applicationCode);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public ResolvedModel resolveModel(Long modelId, String applicationCode) {
        LlmModel model = modelMapper.selectById(modelId);
        if (model == null || !Boolean.TRUE.equals(model.getEnabled())) {
            return null;
        }
        LlmApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<LlmApplication>().eq(LlmApplication::getCode, applicationCode)
        );
        if (application == null || !Boolean.TRUE.equals(application.getEnabled())
                || !isCompatible(application.getModelType(), model)) {
            return null;
        }
        LlmProvider provider = providerMapper.selectById(model.getProviderId());
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            return null;
        }
        return new ResolvedModel(application, model, provider);
    }

    public LlmGatewayConfig.Provider toGatewayProvider(ResolvedModel resolved) {
        LlmGatewayConfig.Provider provider = new LlmGatewayConfig.Provider();
        provider.setProtocol(resolved.provider().getProtocol());
        provider.setBaseUrl(resolved.provider().getBaseUrl());
        provider.setApiKey(resolved.provider().getApiKey());
        provider.setModel(resolved.model().getModelId());
        if (resolved.model().getDimension() != null) {
            provider.setDimension(String.valueOf(resolved.model().getDimension()));
        }
        return provider;
    }

    private boolean isChat(String type) {
        return "chat".equalsIgnoreCase(type);
    }

    private boolean isCompatible(String applicationType, LlmModel model) {
        if (isChat(applicationType)) {
            return !"embedding".equalsIgnoreCase(model.getModelType())
                    && !"rerank".equalsIgnoreCase(model.getModelType());
        }
        return applicationType.equalsIgnoreCase(model.getModelType());
    }

    public record ResolvedModel(LlmApplication application, LlmModel model, LlmProvider provider) {
    }
}
