package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.ImageUnderstandingResult;
import com.demand.system.module.knowledge.service.ImageUnderstandingService;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


/**
 * 基于 OpenAI/Anthropic 兼容多模态接口的图片理解实现。
 * 模型统一从“模型配置 - 模型应用”的工单正文图片理解应用中解析，未配置时自动降级为空结果。
 */
@Service
public class ConfigurableImageUnderstandingService implements ImageUnderstandingService {
    private static final Logger log = LoggerFactory.getLogger(ConfigurableImageUnderstandingService.class);
    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final ObjectMapper objectMapper;

    public ConfigurableImageUnderstandingService(LlmGateway llmGateway,
                                                  LlmModelResolver llmModelResolver,
                                                  ObjectMapper objectMapper) {
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean enabled() {
        return resolveConfiguredModel() != null;
    }

    @Override
    public String unavailableReason() {
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(
                LlmApplicationCode.KNOWLEDGE_IMAGE_UNDERSTANDING
        );
        if (resolved == null) {
            return "模型应用不存在、未启用或没有可用模型";
        }
        if (resolved.application().getModelId() == null) {
            return "模型应用未选择图片理解模型";
        }
        if (!resolved.application().getModelId().equals(resolved.model().getId())) {
            return "模型应用所选图片理解模型不可用";
        }
        return "图片理解模型暂不可用";
    }

    @Override
    public ImageUnderstandingResult analyze(byte[] imageBytes, String contentType, String altText) {
        if (imageBytes == null || imageBytes.length == 0) {
            return ImageUnderstandingResult.empty();
        }
        LlmModelResolver.ResolvedModel resolved = resolveConfiguredModel();
        if (resolved == null) {
            return ImageUnderstandingResult.empty();
        }
        LlmGatewayConfig.Provider provider = llmModelResolver.toGatewayProvider(resolved);
        String prompt = "请分析这张工单正文图片，并严格返回 JSON，不要 Markdown 代码围栏："
                + "{\\\"ocrText\\\":\\\"图片中可读的全部文字，无法识别时为空\\\","
                + "\\\"caption\\\":\\\"用中文描述图片的页面/图表/错误信息/关键数值，无法判断时为空\\\"}."
                + (altText == null || altText.isBlank() ? "" : " 图片 alt 文本是：" + altText);
        try {
            LlmGateway.ChatResult result = llmGateway.chatWithImageWithProvider(
                    provider,
                    "你是工单图片 OCR 与视觉理解服务。只输出合法 JSON。",
                    prompt,
                    contentType,
                    imageBytes
            );
            String content = result == null ? null : result.getContent();
            JsonNode json = parseJson(content);
            if (json == null) {
                log.warn("图片理解服务返回的内容不是合法 JSON，忽略本次结果");
                return ImageUnderstandingResult.empty();
            }
            return new ImageUnderstandingResult(
                    clean(json.path("ocrText").asText("")),
                    clean(json.path("caption").asText(""))
            );
        } catch (Exception e) {
            log.warn("工单正文图片理解失败，降级为正文和 alt 检索: {}", e.getMessage());
            return ImageUnderstandingResult.empty();
        }
    }

    /**
     * 图片模型必须在模型应用中显式选择，避免未配置时回退到普通文本模型。
     */
    private LlmModelResolver.ResolvedModel resolveConfiguredModel() {
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(
                LlmApplicationCode.KNOWLEDGE_IMAGE_UNDERSTANDING
        );
        if (resolved == null || resolved.application().getModelId() == null) {
            return null;
        }
        return resolved.application().getModelId().equals(resolved.model().getId()) ? resolved : null;
    }

    private JsonNode parseJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String value = content.trim();
        if (value.startsWith("```")) {
            int newline = value.indexOf('\n');
            value = newline >= 0 ? value.substring(newline + 1) : value.substring(3);
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3).trim();
            }
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            int start = value.indexOf('{');
            int end = value.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(value.substring(start, end + 1));
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s{3,}", "\\n\\n");
    }
}
