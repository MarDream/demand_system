package com.demand.system.module.knowledge.llm;

import com.demand.system.module.knowledge.llm.LlmGatewayConfig.Protocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGateway {

    private final LlmGatewayConfig config;
    private final ObjectMapper objectMapper;

    // ==================== Embedding ====================

    public List<float[]> embed(List<String> texts) {
        LlmGatewayConfig.Provider provider = config.getEmbedding();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getModel());
            body.put("input", texts);

            JsonNode root = call(provider, "/embeddings", body);
            JsonNode dataNode = root.get("data");

            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : dataNode) {
                JsonNode emb = item.get("embedding");
                float[] vector = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vector[i] = (float) emb.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (Exception e) {
            log.error("Embedding调用失败: model={}", provider.getModel(), e);
            throw new RuntimeException("Embedding调用失败: " + e.getMessage());
        }
    }

    public float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    // ==================== Reranker ====================

    public List<Double> rerank(String query, List<String> documents) {
        LlmGatewayConfig.Provider provider = config.getReranker();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getModel());
            body.put("query", query);
            body.put("documents", documents);

            JsonNode root = call(provider, "/rerank", body);
            JsonNode resultsNode = root.get("results");

            List<Double> scores = new ArrayList<>();
            for (JsonNode item : resultsNode) {
                scores.add(item.get("relevance_score").asDouble());
            }
            return scores;
        } catch (Exception e) {
            log.error("Reranker调用失败: model={}", provider.getModel(), e);
            throw new RuntimeException("Reranker调用失败: " + e.getMessage());
        }
    }

    // ==================== Chat (RAG Answer Generation) ====================

    public String chat(String systemPrompt, String userMessage) {
        LlmGatewayConfig.Provider provider = config.getChat();
        try {
            Protocol protocol = config.resolveProtocol(provider);

            if (protocol == Protocol.ANTHROPIC) {
                return callAnthropicChat(provider, systemPrompt, userMessage, null, null);
            } else {
                return callOpenAIChat(provider, systemPrompt, userMessage, null, null);
            }
        } catch (Exception e) {
            log.error("Chat调用失败: model={}", provider.getModel(), e);
            throw new RuntimeException("Chat调用失败: " + e.getMessage());
        }
    }

    // ==================== Core HTTP Call ====================

    private JsonNode call(LlmGatewayConfig.Provider provider, String path, Map<String, Object> body) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String base = provider.getBaseUrl().replaceAll("/+$", "");
            String url = base + path;
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("LLM API调用失败: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders(LlmGatewayConfig.Provider provider) {
        HttpHeaders headers = new HttpHeaders();
        String apiKey = provider.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return headers;
        }
        Protocol protocol = config.resolveProtocol(provider);
        if (protocol == Protocol.ANTHROPIC) {
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
        } else {
            headers.set("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private String callOpenAIChat(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        try {
            JsonNode root = callOpenAIChatRaw(provider, systemPrompt, userMessage, temperature, maxTokens);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("OpenAI Chat调用失败: " + e.getMessage(), e);
        }
    }

    private String callAnthropicChat(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        try {
            JsonNode root = callAnthropicChatRaw(provider, systemPrompt, userMessage, temperature, maxTokens);
            return root.path("content").path(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Anthropic Chat调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== Chat with arbitrary provider (for test) ====================

    public ChatResult chatWithProvider(LlmGatewayConfig.Provider provider, String systemPrompt, String userMessage) {
        return chatWithProvider(provider, systemPrompt, userMessage, null, null);
    }

    public ChatResult chatWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        long start = System.currentTimeMillis();
        try {
            Protocol protocol = config.resolveProtocol(provider);

            JsonNode root;
            if (protocol == Protocol.ANTHROPIC) {
                root = callAnthropicChatRaw(provider, systemPrompt, userMessage, temperature, maxTokens);
            } else {
                root = callOpenAIChatRaw(provider, systemPrompt, userMessage, temperature, maxTokens);
            }

            long durationMs = System.currentTimeMillis() - start;

            String content;
            JsonNode usageNode;
            if (protocol == Protocol.ANTHROPIC) {
                content = root.path("content").path(0).path("text").asText();
                usageNode = root.path("usage");
            } else {
                content = root.path("choices").path(0).path("message").path("content").asText();
                usageNode = root.path("usage");
            }

            String model = root.path("model").asText(null);

            return ChatResult.builder()
                    .content(content)
                    .durationMs(durationMs)
                    .promptTokens(usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : null)
                    .completionTokens(usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : null)
                    .totalTokens(usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : null)
                    .model(model)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Chat调用失败: " + e.getMessage(), e);
        }
    }

    private JsonNode callOpenAIChatRaw(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", provider.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        body.put("temperature", normalizeTemperature(temperature));
        body.put("max_tokens", normalizeMaxTokens(maxTokens));
        return call(provider, "/chat/completions", body);
    }

    private JsonNode callAnthropicChatRaw(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getModel());
            body.put("max_tokens", normalizeMaxTokens(maxTokens));
            body.put("system", systemPrompt);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", userMessage)
            ));
            body.put("temperature", normalizeTemperature(temperature));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String base = provider.getBaseUrl().replaceAll("/+$", "");
            String url = base + "/messages";
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Anthropic Chat调用失败: " + e.getMessage(), e);
        }
    }

    private double normalizeTemperature(BigDecimal temperature) {
        return temperature != null ? temperature.doubleValue() : 0.3d;
    }

    private int normalizeMaxTokens(Integer maxTokens) {
        return maxTokens != null && maxTokens > 0 ? maxTokens : 2048;
    }

    // ==================== Model List (Sniff) ====================

    public List<ModelInfo> fetchModelList(LlmGatewayConfig.Provider provider) {
        Protocol protocol = config.resolveProtocol(provider);
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String base = provider.getBaseUrl().replaceAll("/+$", "");
            String url;
            if (protocol == Protocol.ANTHROPIC) {
                url = base + "/models";
            } else {
                url = base + "/models";
            }

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            List<ModelInfo> models = new ArrayList<>();
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    String id = item.has("id") ? item.get("id").asText() : null;
                    if (id == null || id.isEmpty()) continue;
                    String ownedBy = item.has("owned_by") ? item.get("owned_by").asText() : null;
                    models.add(new ModelInfo(id, ownedBy));
                }
            }

            // Anthropic: root itself may be array
            if (models.isEmpty() && root.isArray()) {
                for (JsonNode item : root) {
                    String id = item.has("id") ? item.get("id").asText() : null;
                    if (id == null || id.isEmpty()) continue;
                    String ownedBy = item.has("owned_by") ? item.get("owned_by").asText() : null;
                    models.add(new ModelInfo(id, ownedBy));
                }
            }

            return models;
        } catch (Exception e) {
            throw new RuntimeException("获取模型列表失败: " + e.getMessage(), e);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ModelInfo {
        private String id;
        private String ownedBy;
    }

    @lombok.Data
    @lombok.Builder
    public static class ChatResult {
        private String content;
        private long durationMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String model;
    }
}
