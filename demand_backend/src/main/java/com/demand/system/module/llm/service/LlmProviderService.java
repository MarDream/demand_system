package com.demand.system.module.llm.service;

import com.demand.system.module.llm.dto.*;

import java.util.List;

public interface LlmProviderService {
    // Provider
    List<LlmProviderVO> list();
    LlmProviderVO getById(Long id);
    LlmProviderVO create(LlmProviderDTO dto);
    LlmProviderVO update(Long id, LlmProviderDTO dto);
    void delete(Long id);
    void toggleEnabled(Long id);
    String getApiKey(Long id);

    // Model
    LlmModelVO addModel(Long providerId, LlmModelDTO dto);
    LlmModelVO updateModel(Long modelId, LlmModelDTO dto);
    void deleteModel(Long modelId);
    void toggleModelEnabled(Long modelId);
    void toggleModelDefault(Long modelId);
    LlmTestResultVO testModel(Long modelId, LlmTestRequestDTO request);

    // Role
    LlmModelVO getModelByType(String type);

    // Chat Models (for RAG)
    java.util.List<java.util.Map<String, Object>> listChatModels();

    // Sniff
    List<SniffedModelVO> sniffModels(Long id);
}
