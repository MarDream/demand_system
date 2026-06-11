package com.demand.system.module.knowledge.llm;

import com.demand.system.module.knowledge.llm.LlmGatewayConfig.Protocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);
    private static final Pattern V1_PATH_SEGMENT = Pattern.compile("(?i)(^|/)v1($|/)");
    private static final List<String> MODEL_LIST_FIELDS = List.of("data", "models", "model", "items", "results", "list");
    private static final List<String> MODEL_ID_FIELDS = List.of("id", "model", "model_id", "modelId", "name", "value");
    private static final List<String> MODEL_OWNER_FIELDS = List.of(
            "owned_by", "ownedBy", "owner", "provider", "display_name", "displayName", "type"
    );
    private static final List<String> API_ENDPOINT_SUFFIXES = List.of(
            "/chat/completions",
            "/completions",
            "/responses",
            "/embeddings",
            "/messages",
            "/models",
            "/rerank"
    );

    private final LlmGatewayConfig config;
    private final ObjectMapper objectMapper;

    public LlmGateway(LlmGatewayConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

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
            String url = buildApiUrl(provider, path);
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
            String url = buildApiUrl(provider, "/messages");
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
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = buildApiUrl(provider, "/models");

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            return parseModelList(root);
        } catch (Exception e) {
            throw new RuntimeException("获取模型列表失败: " + describeApiException(e), e);
        }
    }

    private String buildApiUrl(LlmGatewayConfig.Provider provider, String path) {
        String base = normalizeApiBaseUrl(provider);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return base + normalizedPath;
    }

    private String normalizeApiBaseUrl(LlmGatewayConfig.Provider provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("API Base URL不能为空");
        }

        String base = stripQueryAndFragment(baseUrl.trim()).replaceAll("/+$", "");
        Protocol protocol = config.resolveProtocol(provider);
        if (protocol == Protocol.OPENAI || protocol == Protocol.ANTHROPIC) {
            return normalizeVersionedApiRoot(base);
        }
        return base;
    }

    private String stripQueryAndFragment(String url) {
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');
        int end = url.length();
        if (queryIndex >= 0) {
            end = Math.min(end, queryIndex);
        }
        if (fragmentIndex >= 0) {
            end = Math.min(end, fragmentIndex);
        }
        return url.substring(0, end);
    }

    private String normalizeVersionedApiRoot(String base) {
        String versionRoot = truncateAfterV1Segment(base);
        if (versionRoot != null) {
            return versionRoot;
        }

        String endpointRoot = stripKnownEndpointSuffix(base);
        versionRoot = truncateAfterV1Segment(endpointRoot);
        if (versionRoot != null) {
            return versionRoot;
        }

        return endpointRoot + "/v1";
    }

    private String truncateAfterV1Segment(String base) {
        try {
            URI uri = URI.create(base);
            String rawPath = uri.getRawPath();
            if (rawPath == null || rawPath.isBlank()) {
                return null;
            }
            Matcher matcher = V1_PATH_SEGMENT.matcher(rawPath);
            if (!matcher.find()) {
                return null;
            }
            int rootEnd = matcher.start() + matcher.group(1).length() + "v1".length();
            String rootPath = rawPath.substring(0, rootEnd);
            return new URI(uri.getScheme(), uri.getRawAuthority(), rootPath, null, null).toString();
        } catch (Exception e) {
            Matcher matcher = V1_PATH_SEGMENT.matcher(base);
            if (!matcher.find()) {
                return null;
            }
            int rootEnd = matcher.start() + matcher.group(1).length() + "v1".length();
            return base.substring(0, rootEnd);
        }
    }

    private String stripKnownEndpointSuffix(String base) {
        String lowerBase = base.toLowerCase(Locale.ROOT);
        for (String suffix : API_ENDPOINT_SUFFIXES) {
            if (lowerBase.endsWith(suffix)) {
                return base.substring(0, base.length() - suffix.length()).replaceAll("/+$", "");
            }
        }
        return base;
    }

    private String describeApiException(Exception e) {
        if (e instanceof RestClientResponseException responseException) {
            String upstreamMessage = extractUpstreamErrorMessage(responseException.getResponseBodyAsString());
            String status = "HTTP " + responseException.getStatusCode().value();
            return upstreamMessage == null ? status : status + ": " + upstreamMessage;
        }
        return e.getMessage();
    }

    private String extractUpstreamErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = firstText(root.path("error"), "message", "type", "code");
            if (message != null) {
                return message;
            }
            message = firstText(root, "message", "msg", "detail", "error_description");
            if (message != null) {
                return message;
            }
            JsonNode error = root.get("error");
            if (error != null && error.isValueNode()) {
                return error.asText();
            }
        } catch (IllegalArgumentException e) {
            // Fall through to a capped raw body; some OpenAI-compatible services return plain text.
        } catch (Exception e) {
            // Fall through to a capped raw body; some OpenAI-compatible services return plain text.
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }

    private List<ModelInfo> parseModelList(JsonNode root) {
        Map<String, ModelInfo> models = new LinkedHashMap<>();
        collectModels(root, models, 0);
        return new ArrayList<>(models.values());
    }

    private void collectModels(JsonNode node, Map<String, ModelInfo> models, int depth) {
        if (node == null || node.isMissingNode() || node.isNull() || depth > 8) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectModelItem(item, models);
                if (item.isObject()) {
                    collectModelsFromKnownFields(item, models, depth + 1);
                }
            }
            return;
        }

        if (node.isObject()) {
            collectModelItem(node, models);
            collectModelsFromKnownFields(node, models, depth + 1);
        }
    }

    private void collectModelsFromKnownFields(JsonNode node, Map<String, ModelInfo> models, int depth) {
        for (String field : MODEL_LIST_FIELDS) {
            JsonNode child = node.get(field);
            if (child != null) {
                if (child.isObject() && ("models".equals(field) || "model".equals(field))) {
                    collectModelMap(child, models);
                }
                collectModels(child, models, depth);
            }
        }
    }

    private void collectModelMap(JsonNode node, Map<String, ModelInfo> models) {
        if (firstText(node, MODEL_ID_FIELDS) != null) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isObject()) {
                String id = firstText(value, MODEL_ID_FIELDS);
                String ownedBy = firstText(value, MODEL_OWNER_FIELDS);
                if (id == null || id.isBlank()) {
                    id = entry.getKey();
                }
                if (!models.containsKey(id)) {
                    models.put(id, new ModelInfo(id, ownedBy));
                }
            } else if (value.isValueNode()) {
                String id = value.asText();
                if ((id == null || id.isBlank()) && entry.getKey() != null && !entry.getKey().isBlank()) {
                    id = entry.getKey();
                }
                if (id != null && !id.isBlank() && !models.containsKey(id)) {
                    models.put(id, new ModelInfo(id, null));
                }
            }
        }
    }

    private void collectModelItem(JsonNode item, Map<String, ModelInfo> models) {
        String id;
        String ownedBy = null;
        if (item.isValueNode()) {
            id = item.asText();
        } else if (item.isObject()) {
            id = firstText(item, MODEL_ID_FIELDS);
            ownedBy = firstText(item, MODEL_OWNER_FIELDS);
        } else {
            return;
        }

        if (id == null || id.isBlank() || models.containsKey(id)) {
            return;
        }
        models.put(id, new ModelInfo(id, ownedBy));
    }

    private String firstText(JsonNode node, List<String> fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        return firstText(node, Arrays.asList(fields));
    }

    public static class ModelInfo {
        private String id;
        private String ownedBy;

        public ModelInfo() {}

        public ModelInfo(String id, String ownedBy) {
            this.id = id;
            this.ownedBy = ownedBy;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOwnedBy() { return ownedBy; }
        public void setOwnedBy(String ownedBy) { this.ownedBy = ownedBy; }
    }

    public static class ChatResult {
        private String content;
        private long durationMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String model;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public Integer getPromptTokens() { return promptTokens; }
        public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
        public Integer getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
        public Integer getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String content;
            private long durationMs;
            private Integer promptTokens;
            private Integer completionTokens;
            private Integer totalTokens;
            private String model;

            public Builder content(String content) { this.content = content; return this; }
            public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
            public Builder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
            public Builder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
            public Builder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public ChatResult build() {
                ChatResult r = new ChatResult();
                r.content = this.content;
                r.durationMs = this.durationMs;
                r.promptTokens = this.promptTokens;
                r.completionTokens = this.completionTokens;
                r.totalTokens = this.totalTokens;
                r.model = this.model;
                return r;
            }
        }
    }
}
