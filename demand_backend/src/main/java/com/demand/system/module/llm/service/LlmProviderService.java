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

    // Translate (for role code generation)
    /**
     * 使用已配置的默认 Chat 模型将中文文本翻译为英文。
     * 若未配置可用模型则返回 null，由调用方走本地 fallback。
     *
     * @param chineseText 中文文本（如角色名称）
     * @return 英文翻译结果（大写下划线格式），或 null 表示无可用模型
     */
    String translateToEnglish(String chineseText);

    // Sniff
    List<SniffedModelVO> sniffModels(Long id);
}
