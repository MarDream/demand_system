package com.demand.system.module.knowledge.llm;

import com.demand.system.module.knowledge.llm.LlmGatewayConfig.Protocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);
    private static final Pattern V1_PATH_SEGMENT = Pattern.compile("(?i)(^|/)v1($|/)");
    /** Matches any versioned path segment like /v1, /v2, /v4, /v1beta1 etc. */
    private static final Pattern VERSION_PATH_SEGMENT = Pattern.compile("(?i)(^|/)v\\d+[a-z\\d]*($|/)");
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
    private final RestTemplate sharedRestTemplate;

    public LlmGateway(LlmGatewayConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(120));
        this.sharedRestTemplate = new RestTemplate(requestFactory);
    }

    public LlmGatewayConfig getConfig() {
        return config;
    }

    // ==================== Embedding ====================

    /**
     * @deprecated Embedding 模型必须由上层服务从数据库模型配置解析后传入 Provider，禁止回退 YML 固定配置。
     */
    @Deprecated(forRemoval = true)
    public List<float[]> embed(List<String> texts) {
        throw new UnsupportedOperationException("Embedding 模型必须从数据库模型配置读取，请使用 embedWithProvider(provider, texts)");
    }

    /**
     * @deprecated Embedding 模型必须由上层服务从数据库模型配置解析后传入 Provider，禁止回退 YML 固定配置。
     */
    @Deprecated(forRemoval = true)
    public float[] embed(String text) {
        throw new UnsupportedOperationException("Embedding 模型必须从数据库模型配置读取，请使用 embedWithProvider(provider, text)");
    }

    /**
     * 使用指定的 Provider 配置调用 Embedding 接口，
     * 支持从数据库动态选择模型（而非绑定 YAML 配置）。
     */
    public List<float[]> embedWithProvider(LlmGatewayConfig.Provider provider, List<String> texts) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getModel());
            body.put("input", texts);
            addEmbeddingDimensionsIfSupported(provider, body);

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

    public float[] embedWithProvider(LlmGatewayConfig.Provider provider, String text) {
        return embedWithProvider(provider, List.of(text)).get(0);
    }

    // ==================== Reranker ====================

    /**
     * @deprecated Reranker 模型必须由上层服务从数据库模型配置解析后传入 Provider，禁止回退 YML 固定配置。
     */
    @Deprecated(forRemoval = true)
    public List<Double> rerank(String query, List<String> documents) {
        throw new UnsupportedOperationException("Reranker 模型必须从数据库模型配置读取，请使用 rerankWithProvider(provider, query, documents)");
    }

    /**
     * 使用指定的 Provider 配置调用 Reranker 接口，
     * 支持从数据库动态选择模型（而非绑定 YAML 配置）。
     */
    public List<Double> rerankWithProvider(LlmGatewayConfig.Provider provider, String query, List<String> documents) {
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

    // ==================== Event Extraction (SAG) ====================

    /**
     * 从文档chunk中提取事件和实体（SAG事件提取）。
     * 调用 LLM 要求其返回 JSON 格式的事件和实体。
     *
     * @param provider   LLM provider配置（从数据库解析）
     * @param chunkText  chunk原文
     * @param chunkIndex chunk序号
     * @param docTitle   文档标题（用于上下文）
     * @return JSON字符串（{"events":[...], "entities":[...]}）
     */
    public String extractEventsFromChunk(
            LlmGatewayConfig.Provider provider,
            String chunkText,
            int chunkIndex,
            String docTitle
    ) {
        try {
            String systemPrompt = """
                    你是一个专业的内容理解助手，擅长从文档片段中提取结构化的事件和实体信息。

                    请仔细分析以下文档片段，提取所有有意义的事件和实体。

                    提取规则：
                    1. 事件提取：
                       - 从片段中识别出所有具有完整语义的事件
                       - 每个事件应包含：title（事件标题）、summary（一句话摘要）、content（详细描述，原文摘录）
                       - category 从以下选一个：需求/缺陷/变更/决策/测试/部署/其他
                       - priority 从以下选一个：high/medium/low
                       - keywords：提取3-5个核心关键词
                       - references：提及的相关文档、系统或人员列表

                    2. 实体提取：
                       - 识别事件中提到的重要实体（人员、组织、系统、产品、指标等）
                       - type 从以下选一个：person/organization/product/metric/system/action/work/group/subject/tags
                       - 每个实体包含：type、name、description（描述）

                    输出格式（严格 JSON）：
                    {
                      "events": [
                        {
                          "title": "事件标题",
                          "summary": "一句话摘要",
                          "content": "详细描述",
                          "category": "需求",
                          "priority": "medium",
                          "keywords": ["关键词1","关键词2"],
                          "references": ["引用1","引用2"],
                          "entities": [
                            {"type": "product", "name": "系统名称", "description": "描述"},
                            {"type": "person", "name": "人员名称", "description": "描述"}
                          ]
                        }
                      ]
                    }

                    注意：如果片段中没有任何有意义的事件或实体，请返回空数组。
                    重要：必须直接返回 JSON，不要额外解释。
                    """;

            String userMessage = String.format(
                    "文档标题：%s\nChunk序号：%d\n\n文档内容：\n%s",
                    docTitle != null && !docTitle.isBlank() ? docTitle : "未知文档",
                    chunkIndex,
                    chunkText
            );

            // 构建 OpenAI chat 请求
            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getModel());
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));
            body.put("temperature", 0.1);  // 低温度确保输出稳定
            body.put("max_tokens", 2048);

            JsonNode root = call(provider, "/chat/completions", body);
            String content = root.path("choices").path(0).path("message").path("content").asText();

            if (content == null || content.isBlank()) {
                return "{\"events\":[],\"entities\":[]}";
            }

            // 清理可能的 markdown 代码块包裹
            content = cleanJsonResponse(content);

            // 验证是合法 JSON
            try {
                objectMapper.readTree(content);
                return content;
            } catch (Exception e) {
                log.warn("LLM返回内容非合法JSON，尝试提取JSON片段: {}", content.substring(0, Math.min(200, content.length())));
                // 尝试从 markdown 代码块或文本中提取 JSON
                return extractJsonFromText(content);
            }
        } catch (Exception e) {
            log.error("事件提取失败: model={}, error={}", provider.getModel(), e.getMessage());
            // 返回空结果而非抛出异常，确保 ingestion 不中断
            return "{\"events\":[],\"entities\":[]}";
        }
    }

    /**
     * 清理 LLM 返回内容：移除 markdown 代码块包裹、前后空白。
     */
    private String cleanJsonResponse(String raw) {
        if (raw == null) return "{\"events\":[],\"entities\":[]}";
        String s = raw.trim();
        // 移除 ```json ... ``` 或 ``` ... ``` 包裹
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    /**
     * 从可能包含非 JSON 内容的文本中提取 JSON 对象。
     */
    private String extractJsonFromText(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                String candidate = text.substring(start, end + 1);
                objectMapper.readTree(candidate);
                return candidate;
            } catch (Exception ignored) {}
        }
        return "{\"events\":[],\"entities\":[]}";
    }

    // ==================== Chat (RAG Answer Generation) ====================

    /**
     * @deprecated Chat 模型必须由上层服务从数据库模型配置解析后传入 Provider，禁止回退 YML 固定配置。
     */
    @Deprecated(forRemoval = true)
    public String chat(String systemPrompt, String userMessage) {
        throw new UnsupportedOperationException("Chat 模型必须从数据库模型配置读取，请使用 chatWithProvider(...)");
    }

    /**
     * @deprecated Chat 模型必须由上层服务从数据库模型配置解析后传入 Provider，禁止回退 YML 固定配置。
     */
    @Deprecated(forRemoval = true)
    public void streamChat(String systemPrompt, String userMessage, Consumer<String> tokenConsumer) {
        throw new UnsupportedOperationException("Chat 模型必须从数据库模型配置读取，请使用 streamChatWithProvider(...)");
    }

    // ==================== Core HTTP Call ====================

    private JsonNode call(LlmGatewayConfig.Provider provider, String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = buildApiUrl(provider, path);
            log.info("LLM API 调用: url={}, model={}, body={}", url, provider.getModel(), body);
            ResponseEntity<String> response = sharedRestTemplate.postForEntity(url, entity, String.class);
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
            throw new RuntimeException("OpenAI Chat调用失败: " + describeApiException(e), e);
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
            throw new RuntimeException("Anthropic Chat调用失败: " + describeApiException(e), e);
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
            throw new RuntimeException("Chat调用失败: " + describeApiException(e), e);
        }
    }

    public void streamChatWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens,
            Consumer<String> tokenConsumer
    ) {
        Protocol protocol = config.resolveProtocol(provider);
        Map<String, Object> body = protocol == Protocol.ANTHROPIC
                ? buildAnthropicChatBody(provider, systemPrompt, userMessage, temperature, maxTokens)
                : buildOpenAIChatBody(provider, systemPrompt, userMessage, temperature, maxTokens);
        body.put("stream", true);
        String path = protocol == Protocol.ANTHROPIC ? "/messages" : "/chat/completions";
        streamCall(provider, path, body, protocol, tokenConsumer);
    }

    private JsonNode callOpenAIChatRaw(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        Map<String, Object> body = buildOpenAIChatBody(provider, systemPrompt, userMessage, temperature, maxTokens);
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
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = buildAnthropicChatBody(provider, systemPrompt, userMessage, temperature, maxTokens);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = buildApiUrl(provider, "/messages");
            ResponseEntity<String> response = sharedRestTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Anthropic Chat调用失败: " + describeApiException(e), e);
        }
    }

    private Map<String, Object> buildOpenAIChatBody(
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
        addSiliconFlowChatCompatibility(provider, body);
        return body;
    }

    private void addSiliconFlowChatCompatibility(LlmGatewayConfig.Provider provider, Map<String, Object> body) {
        if (!isSiliconFlowProvider(provider)) {
            return;
        }
        // SiliconFlow 官方 Chat Completions 文档对 Qwen3/DeepSeek/GLM 等思考模型支持 enable_thinking。
        // 批量连通性测试只需要快速确认可用性，关闭思考模式可避免部分模型返回空 content 或测试耗时过长。
        body.put("enable_thinking", false);
    }

    private void addEmbeddingDimensionsIfSupported(LlmGatewayConfig.Provider provider, Map<String, Object> body) {
        if (!isSiliconFlowProvider(provider)) {
            return;
        }
        String dimension = provider.getDimension();
        if (dimension == null || dimension.isBlank()) {
            return;
        }
        try {
            body.put("dimensions", Integer.parseInt(dimension.trim()));
        } catch (NumberFormatException e) {
            log.warn("忽略非法的 SiliconFlow Embedding 维度配置: model={}, dimension={}", provider.getModel(), dimension);
        }
    }

    private boolean isSiliconFlowProvider(LlmGatewayConfig.Provider provider) {
        String baseUrl = provider == null ? null : provider.getBaseUrl();
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("siliconflow");
    }

    private Map<String, Object> buildAnthropicChatBody(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", provider.getModel());
        body.put("max_tokens", normalizeMaxTokens(maxTokens));
        body.put("system", systemPrompt);
        body.put("messages", List.of(
                Map.of("role", "user", "content", userMessage)
        ));
        body.put("temperature", normalizeTemperature(temperature));
        return body;
    }

    private void streamCall(
            LlmGatewayConfig.Provider provider,
            String path,
            Map<String, Object> body,
            Protocol protocol,
            Consumer<String> tokenConsumer
    ) {
        HttpHeaders headers = buildHeaders(provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = buildApiUrl(provider, path);

        sharedRestTemplate.execute(url, HttpMethod.POST, request -> {
            request.getHeaders().putAll(headers);
            objectMapper.writeValue(request.getBody(), entity.getBody());
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("LLM API流式调用失败: HTTP " + response.getStatusCode().value());
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleStreamLine(line, protocol, tokenConsumer);
                }
            }
            return null;
        });
    }

    private void handleStreamLine(String line, Protocol protocol, Consumer<String> tokenConsumer) {
        if (line == null || line.isBlank() || line.startsWith(":")) {
            return;
        }
        if (!line.startsWith("data:")) {
            return;
        }

        String payload = line.substring("data:".length()).trim();
        if (payload.isBlank() || "[DONE]".equals(payload)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String delta = protocol == Protocol.ANTHROPIC ? extractAnthropicDelta(root) : extractOpenAIDelta(root);
            if (delta != null && !delta.isEmpty()) {
                tokenConsumer.accept(delta);
            }
        } catch (Exception e) {
            log.debug("忽略无法解析的LLM流式片段: {}", payload, e);
        }
    }

    private String extractOpenAIDelta(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode deltaNode = choice.path("delta").path("content");
        if (deltaNode.isTextual()) {
            return deltaNode.asText();
        }
        JsonNode messageNode = choice.path("message").path("content");
        if (messageNode.isTextual()) {
            return messageNode.asText();
        }
        JsonNode textNode = choice.path("text");
        return textNode.isTextual() ? textNode.asText() : null;
    }

    private String extractAnthropicDelta(JsonNode root) {
        JsonNode textNode = root.path("delta").path("text");
        if (textNode.isTextual()) {
            return textNode.asText();
        }
        JsonNode contentNode = root.path("content_block").path("text");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            JsonNode firstText = content.path(0).path("text");
            if (firstText.isTextual()) {
                return firstText.asText();
            }
        }
        return null;
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
            HttpHeaders headers = buildHeaders(provider);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = buildApiUrl(provider, "/models");

            ResponseEntity<String> response = sharedRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);
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
            String result = normalizeVersionedApiRoot(base);
            log.info("normalizeApiBaseUrl: input={}, protocol={}, result={}", base, protocol, result);
            return result;
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
        // 1. If the URL already contains /v1, truncate after it
        String versionRoot = truncateAfterV1Segment(base);
        if (versionRoot != null) {
            return versionRoot;
        }

        // 2. Strip known endpoint suffixes (e.g. /chat/completions) and check again
        String endpointRoot = stripKnownEndpointSuffix(base);
        versionRoot = truncateAfterV1Segment(endpointRoot);
        if (versionRoot != null) {
            return versionRoot;
        }

        // 3. If the URL already contains a versioned segment (e.g. /v4, /v2, /v1beta1),
        //    don't append /v1 — the provider uses a non-standard version path (e.g. ZhiPu /v4)
        if (hasVersionedPathSegment(endpointRoot)) {
            return endpointRoot;
        }

        // 4. No version segment found at all, append /v1 (standard OpenAI convention)
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

    private boolean hasVersionedPathSegment(String url) {
        try {
            String path = URI.create(url).getRawPath();
            return path != null && VERSION_PATH_SEGMENT.matcher(path).find();
        } catch (Exception e) {
            return VERSION_PATH_SEGMENT.matcher(url).find();
        }
    }

    private String describeApiException(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof RestClientResponseException responseException) {
                String upstreamMessage = extractUpstreamErrorMessage(responseException.getResponseBodyAsString());
                String status = "HTTP " + responseException.getStatusCode().value();
                return upstreamMessage == null ? status : status + ": " + upstreamMessage;
            }
            cause = cause.getCause();
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
                Long contextWindow = firstLong(value, "context_window", "contextWindow", "context_length", "max_context_length");
                Long created = firstLong(value, "created", "created_at", "createdAt");
                if (id == null || id.isBlank()) {
                    id = entry.getKey();
                }
                if (!models.containsKey(id)) {
                    ModelInfo info = new ModelInfo(id, ownedBy);
                    info.setContextWindow(contextWindow);
                    info.setCreated(created);
                    models.put(id, info);
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
        Long contextWindow = null;
        Long created = null;
        if (item.isValueNode()) {
            id = item.asText();
        } else if (item.isObject()) {
            id = firstText(item, MODEL_ID_FIELDS);
            ownedBy = firstText(item, MODEL_OWNER_FIELDS);
            contextWindow = firstLong(item, "context_window", "contextWindow", "context_length", "max_context_length");
            created = firstLong(item, "created", "created_at", "createdAt");
        } else {
            return;
        }

        if (id == null || id.isBlank() || models.containsKey(id)) {
            return;
        }
        ModelInfo info = new ModelInfo(id, ownedBy);
        info.setContextWindow(contextWindow);
        info.setCreated(created);
        models.put(id, info);
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

    private Long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode()) {
                if (value.isNumber()) {
                    long v = value.asLong();
                    if (v > 0) return v;
                }
                // 有些厂商返回字符串形式的数字
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    try {
                        long v = Long.parseLong(text);
                        if (v > 0) return v;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }

    public static class ModelInfo {
        private String id;
        private String ownedBy;
        private Long contextWindow;
        private Long created;

        public ModelInfo() {}

        public ModelInfo(String id, String ownedBy) {
            this.id = id;
            this.ownedBy = ownedBy;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getOwnedBy() { return ownedBy; }
        public void setOwnedBy(String ownedBy) { this.ownedBy = ownedBy; }
        public Long getContextWindow() { return contextWindow; }
        public void setContextWindow(Long contextWindow) { this.contextWindow = contextWindow; }
        public Long getCreated() { return created; }
        public void setCreated(Long created) { this.created = created; }
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
