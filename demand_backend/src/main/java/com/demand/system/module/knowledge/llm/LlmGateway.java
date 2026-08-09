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
            } catch (Exception e) {
                log.debug("JSON 候选片段解析失败，将返回空结果", e);
            }
        }
        return "{\"events\":[],\"entities\":[]}";
    }

    // ==================== Chat (RAG Answer Generation) ====================

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

    /**
     * 调用兼容 OpenAI/Anthropic 协议的多模态对话接口。
     * 图片只在调用已配置的视觉 provider 时上传；调用方负责限制图片大小和类型。
     */
    public ChatResult chatWithImageWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            String contentType,
            byte[] imageBytes
    ) {
        if (provider == null || imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("图片理解 provider 或图片内容为空");
        }
        long start = System.currentTimeMillis();
        try {
            Protocol protocol = config.resolveProtocol(provider);
            String mime = contentType == null || contentType.isBlank()
                    ? "application/octet-stream" : contentType.toLowerCase(Locale.ROOT);
            String encoded = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> body = new HashMap<>();
            body.put("model", provider.getModel());
            if (protocol == Protocol.ANTHROPIC) {
                body.put("max_tokens", 2048);
                body.put("messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image", "source", Map.of(
                                        "type", "base64", "media_type", mime, "data", encoded
                                )),
                                Map.of("type", "text", "text", userMessage)
                        )
                )));
                body.put("system", systemPrompt == null ? "" : systemPrompt);
                JsonNode root = call(provider, "/messages", body);
                return parseChatResult(root, protocol, System.currentTimeMillis() - start);
            }
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt),
                    Map.of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", userMessage),
                            Map.of("type", "image_url", "image_url", Map.of(
                                    "url", "data:" + mime + ";base64," + encoded
                            ))
                    ))
            ));
            body.put("temperature", 0.0);
            body.put("max_tokens", 2048);
            JsonNode root = call(provider, "/chat/completions", body);
            return parseChatResult(root, protocol, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException("图片理解调用失败: " + describeApiException(e), e);
        }
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
            return parseChatResult(root, protocol, durationMs);
        } catch (Exception e) {
            throw new RuntimeException("Chat调用失败: " + describeApiException(e), e);
        }
    }

    /**
     * 非流式对话（带深度思考参数）。
     * <p>extraBodyParams 合并规则与流式一致：OpenAI 协议全量合并，Anthropic 协议仅合并 thinking 键。
     * 若目标端点不支持 thinking 参数会抛异常，由调用方决定是否降级重试。</p>
     */
    public ChatResult chatWithProviderWithThinking(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens,
            Map<String, Object> extraBodyParams
    ) {
        long start = System.currentTimeMillis();
        try {
            Protocol protocol = config.resolveProtocol(provider);
            Map<String, Object> body = protocol == Protocol.ANTHROPIC
                    ? buildAnthropicChatBody(provider, systemPrompt, userMessage, temperature, maxTokens)
                    : buildOpenAIChatBody(provider, systemPrompt, userMessage, temperature, maxTokens);
            if (extraBodyParams != null && !extraBodyParams.isEmpty()) {
                if (protocol == Protocol.ANTHROPIC) {
                    extraBodyParams.forEach((key, value) -> {
                        if (key.toLowerCase(Locale.ROOT).contains("thinking")) {
                            body.put(key, value);
                        }
                    });
                } else {
                    body.putAll(extraBodyParams);
                }
            }
            String path = protocol == Protocol.ANTHROPIC ? "/messages" : "/chat/completions";
            JsonNode root = call(provider, path, body);
            long durationMs = System.currentTimeMillis() - start;
            return parseChatResult(root, protocol, durationMs);
        } catch (Exception e) {
            throw new RuntimeException("Chat(thinking)调用失败: " + describeApiException(e), e);
        }
    }

    private ChatResult parseChatResult(JsonNode root, Protocol protocol, long durationMs) {
        String content;
        String reasoning = null;
        JsonNode usageNode;
        if (protocol == Protocol.ANTHROPIC) {
            // 启用 thinking 后 content 数组首个 block 可能是 thinking，需按 type 区分
            content = extractAnthropicText(root);
            reasoning = extractAnthropicThinking(root);
            usageNode = root.path("usage");
        } else {
            content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode reasoningNode = root.path("choices").path(0).path("message").path("reasoning_content");
            if (reasoningNode.isTextual()) {
                reasoning = reasoningNode.asText();
            }
            usageNode = root.path("usage");
        }

        String model = root.path("model").asText(null);

        return ChatResult.builder()
                .content(content)
                .reasoningContent(reasoning)
                .durationMs(durationMs)
                .promptTokens(usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : null)
                .completionTokens(usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : null)
                .totalTokens(usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : null)
                .model(model)
                .build();
    }

    public void streamChatWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens,
            Consumer<String> tokenConsumer
    ) {
        streamChatWithProvider(provider, systemPrompt, userMessage, temperature, maxTokens, tokenConsumer, null);
    }

    /**
     * 流式对话（带 token 用量统计）。
     *
     * @param usageConsumer 流结束后收到累计的 token 用量（可能为 null，表示接口未返回 usage）
     */
    public void streamChatWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens,
            Consumer<String> tokenConsumer,
            Consumer<ChatUsage> usageConsumer
    ) {
        streamChatWithProvider(provider, systemPrompt, userMessage, temperature, maxTokens, null, tokenConsumer, usageConsumer);
    }

    /**
     * 流式对话（带 token 用量统计 + 额外请求体参数）。
     * <p>额外参数仅在 OpenAI 兼容协议下合并到请求体，用于注入厂商专属的联网搜索等开关，
     * 例如智谱的 {@code web_search:true}、通义千问的 {@code enable_search:true}。
     * 可通过 {@link #buildWebSearchParams(LlmGatewayConfig.Provider)} 获取对应厂商的参数。
     *
     * @param extraBodyParams 额外请求体参数，null 或空表示不追加
     * @param usageConsumer   流结束后收到累计的 token 用量（可能为 null，表示接口未返回 usage）
     */
    public void streamChatWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens,
            Map<String, Object> extraBodyParams,
            Consumer<String> tokenConsumer,
            Consumer<ChatUsage> usageConsumer
    ) {
        streamChatWithProvider(provider, systemPrompt, userMessage, temperature, maxTokens, extraBodyParams, tokenConsumer, null, usageConsumer);
    }

    /**
     * 流式对话（带 token 用量统计 + 额外请求体参数 + 深度思考 reasoning 增量回调）。
     * <p>extraBodyParams 合并规则：OpenAI 兼容协议全量合并；Anthropic 协议仅合并含 {@code thinking} 的条目
     * （避免 web_search 等 OpenAI 专属键污染 anthropic 请求体）。</p>
     *
     * @param extraBodyParams  额外请求体参数（如联网搜索、深度思考开关），null 或空表示不追加
     * @param reasoningConsumer 深度思考内容增量回调，可为 null（不解析 reasoning）
     * @param usageConsumer     流结束后收到累计的 token 用量，可为 null
     */
    public void streamChatWithProvider(
            LlmGatewayConfig.Provider provider,
            String systemPrompt,
            String userMessage,
            BigDecimal temperature,
            Integer maxTokens,
            Map<String, Object> extraBodyParams,
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer,
            Consumer<ChatUsage> usageConsumer
    ) {
        Protocol protocol = config.resolveProtocol(provider);
        Map<String, Object> body = protocol == Protocol.ANTHROPIC
                ? buildAnthropicChatBody(provider, systemPrompt, userMessage, temperature, maxTokens)
                : buildOpenAIChatBody(provider, systemPrompt, userMessage, temperature, maxTokens);
        body.put("stream", true);
        if (protocol == Protocol.OPENAI) {
            // OpenAI 兼容接口：开启 usage 统计，接口会在流末返回 usage 帧
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (extraBodyParams != null && !extraBodyParams.isEmpty()) {
            if (protocol == Protocol.ANTHROPIC) {
                // Anthropic 协议仅注入 thinking 相关参数，其余（如 web_search）忽略
                extraBodyParams.forEach((key, value) -> {
                    if (key.toLowerCase(Locale.ROOT).contains("thinking")) {
                        body.put(key, value);
                    }
                });
            } else {
                body.putAll(extraBodyParams);
            }
        }
        String path = protocol == Protocol.ANTHROPIC ? "/messages" : "/chat/completions";
        streamCall(provider, path, body, protocol, tokenConsumer, reasoningConsumer, usageConsumer);
    }

    /**
     * 根据 Provider 的 baseUrl / model 检测厂商，返回对应的联网搜索请求体参数。
     * <ul>
     *   <li>智谱 GLM (bigmodel/zhipu)：{@code web_search:true}</li>
     *   <li>通义千问 (dashscope/aliyun)：{@code enable_search:true}</li>
     *   <li>月之暗面 Kimi (moonshot)：内置 {@code $web_search} 工具</li>
     *   <li>SiliconFlow 硅基流动：{@code enable_search:true}（部分模型支持）</li>
     *   <li>其它 OpenAI 兼容服务：默认尝试 {@code web_search:true}（不支持时会被忽略）</li>
     * </ul>
     *
     * @return 联网搜索参数 Map；若 provider 为空则返回默认 {@code web_search:true}
     */
    public Map<String, Object> buildWebSearchParams(LlmGatewayConfig.Provider provider) {
        Map<String, Object> params = new HashMap<>();
        if (provider == null) {
            params.put("web_search", true);
            return params;
        }
        String baseUrl = provider.getBaseUrl() == null ? "" : provider.getBaseUrl().toLowerCase(Locale.ROOT);
        String model = provider.getModel() == null ? "" : provider.getModel().toLowerCase(Locale.ROOT);

        if (baseUrl.contains("bigmodel") || baseUrl.contains("zhipu")) {
            // 智谱 GLM：web_search: true（也可用 web_search: { enable: true, search_result: true }）
            params.put("web_search", true);
        } else if (baseUrl.contains("dashscope") || baseUrl.contains("aliyuncs") || model.contains("qwen")) {
            // 通义千问 DashScope 兼容模式：enable_search: true
            params.put("enable_search", true);
        } else if (baseUrl.contains("moonshot") || model.contains("kimi")) {
            // 月之暗面 Kimi：通过内置工具 $web_search 触发联网
            params.put("tools", List.of(Map.of(
                    "type", "builtin_function",
                    "function", Map.of("name", "$web_search")
            )));
        } else if (baseUrl.contains("siliconflow")) {
            // 硅基流动：部分模型（如 Qwen 系）支持 enable_search
            params.put("enable_search", true);
        } else {
            // 兜底：尝试 web_search: true，多数 OpenAI 兼容服务会忽略不认识的字段
            params.put("web_search", true);
        }
        return params;
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
        return body;
    }

    /**
     * 构造"深度思考"请求体参数。
     * <ul>
     *   <li>OpenAI 兼容协议：注入 {@code enable_thinking:true}（SiliconFlow/Qwen 等思考模型支持，不认识的厂商会忽略）</li>
     *   <li>Anthropic 协议：注入 {@code thinking:{type:enabled,budget_tokens:N}}（Claude 原生思考模式，
     *       budget 取 maxTokens 的一半且不低于 512；第三方 anthropic 兼容端点若不支持会返回 4xx，
     *       由调用方捕获后降级为不带 thinking 重试）</li>
     * </ul>
     *
     * @param maxTokens 当前请求的 max_tokens，用于计算 Anthropic 思考预算；null 时按默认 2048 计算
     * @return 思考参数 Map（可为空 map，表示无需注入）
     */
    public Map<String, Object> buildThinkingParams(LlmGatewayConfig.Provider provider, Integer maxTokens) {
        Map<String, Object> params = new HashMap<>();
        if (provider == null) {
            return params;
        }
        Protocol protocol = config.resolveProtocol(provider);
        if (protocol == Protocol.ANTHROPIC) {
            int maxTokensValue = normalizeMaxTokens(maxTokens);
            int budget = Math.max(512, Math.min(2048, maxTokensValue / 2));
            params.put("thinking", Map.of(
                    "type", "enabled",
                    "budget_tokens", budget
            ));
        } else {
            // OpenAI 兼容：思考模型开关（Qwen3/DeepSeek/GLM 等 SiliconFlow 系支持）
            params.put("enable_thinking", true);
        }
        return params;
    }

    /**
     * Anthropic 非流式响应中提取正文文本（跳过 thinking block）。
     */
    private String extractAnthropicText(JsonNode root) {
        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) {
            return root.path("content").path(0).path("text").asText();
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("text".equals(type) || type.isBlank()) {
                String text = block.path("text").asText("");
                if (!text.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }

    /**
     * Anthropic 非流式响应中提取思考（thinking）内容，拼接所有 thinking block 的 thinking 字段。
     */
    private String extractAnthropicThinking(JsonNode root) {
        JsonNode content = root.path("content");
        if (!content.isArray() || content.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode block : content) {
            String type = block.path("type").asText("");
            if ("thinking".equals(type)) {
                String thinking = block.path("thinking").asText("");
                if (!thinking.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(thinking);
                }
            }
        }
        return builder.length() > 0 ? builder.toString() : null;
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
            Consumer<String> tokenConsumer,
            Consumer<String> reasoningConsumer,
            Consumer<ChatUsage> usageConsumer
    ) {
        HttpHeaders headers = buildHeaders(provider);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = buildApiUrl(provider, path);

        ChatUsage usage = new ChatUsage();

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
                    handleStreamLine(line, protocol, tokenConsumer, reasoningConsumer, usage);
                }
            }
            return null;
        });

        if (usageConsumer != null && (usage.getPromptTokens() != null || usage.getCompletionTokens() != null)) {
            usageConsumer.accept(usage);
        }
    }

    private void handleStreamLine(String line, Protocol protocol, Consumer<String> tokenConsumer, Consumer<String> reasoningConsumer, ChatUsage usage) {
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
            if (reasoningConsumer != null) {
                String reasoningDelta = protocol == Protocol.ANTHROPIC
                        ? extractAnthropicReasoningDelta(root)
                        : extractOpenAIReasoningDelta(root);
                if (reasoningDelta != null && !reasoningDelta.isEmpty()) {
                    reasoningConsumer.accept(reasoningDelta);
                }
            }
            accumulateUsage(root, protocol, usage);
        } catch (Exception e) {
            log.debug("忽略无法解析的LLM流式片段: {}", payload, e);
        }
    }

    /**
     * OpenAI 兼容协议（DeepSeek/Qwen 等思考模型）：提取 choices[0].delta.reasoning_content 增量。
     */
    private String extractOpenAIReasoningDelta(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode deltaNode = choice.path("delta").path("reasoning_content");
        if (deltaNode.isTextual()) {
            return deltaNode.asText();
        }
        JsonNode messageNode = choice.path("message").path("reasoning_content");
        if (messageNode.isTextual()) {
            return messageNode.asText();
        }
        return null;
    }

    /**
     * Anthropic 协议：提取 content_block_delta 事件中 delta.thinking 增量，
     * 以及 content_block 中 type=thinking 的文本。
     */
    private String extractAnthropicReasoningDelta(JsonNode root) {
        JsonNode deltaThinking = root.path("delta").path("thinking");
        if (deltaThinking.isTextual()) {
            return deltaThinking.asText();
        }
        JsonNode contentBlock = root.path("content_block");
        if (contentBlock.isObject() && "thinking".equals(contentBlock.path("type").asText(""))) {
            JsonNode text = contentBlock.path("thinking");
            if (text.isTextual()) {
                return text.asText();
            }
        }
        // 兼容部分实现直接放在 content[0].thinking
        JsonNode content = root.path("content");
        if (content.isArray() && content.size() > 0) {
            JsonNode first = content.path(0);
            if ("thinking".equals(first.path("type").asText(""))) {
                JsonNode text = first.path("thinking");
                if (text.isTextual()) {
                    return text.asText();
                }
            }
        }
        return null;
    }

    /**
     * 从流式帧中累加 token 用量（取覆盖值，非增量）：
     * - OpenAI：流末 usage 帧携带 prompt_tokens / completion_tokens / total_tokens
     * - Anthropic：message_start 携带 usage.input_tokens，message_delta 携带累计的 usage.output_tokens
     */
    private void accumulateUsage(JsonNode root, Protocol protocol, ChatUsage usage) {
        JsonNode usageNode = root.path("usage");
        if (usageNode == null || !usageNode.isObject() || usageNode.isMissingNode()) {
            return;
        }
        if (protocol == Protocol.ANTHROPIC) {
            if (usageNode.has("input_tokens")) {
                usage.setPromptTokens(usageNode.get("input_tokens").asInt());
            }
            if (usageNode.has("output_tokens")) {
                usage.setCompletionTokens(usageNode.get("output_tokens").asInt());
            }
        } else {
            if (usageNode.has("prompt_tokens")) {
                usage.setPromptTokens(usageNode.get("prompt_tokens").asInt());
            }
            if (usageNode.has("completion_tokens")) {
                usage.setCompletionTokens(usageNode.get("completion_tokens").asInt());
            }
            if (usageNode.has("total_tokens")) {
                usage.setTotalTokens(usageNode.get("total_tokens").asInt());
            }
        }
        if (usage.getTotalTokens() == null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null) {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
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

    public static class ChatUsage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;

        public Integer getPromptTokens() { return promptTokens; }
        public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
        public Integer getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
        public Integer getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    }

    public static class ChatResult {
        private String content;
        private String reasoningContent;
        private long durationMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String model;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getReasoningContent() { return reasoningContent; }
        public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }
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
            private String reasoningContent;
            private long durationMs;
            private Integer promptTokens;
            private Integer completionTokens;
            private Integer totalTokens;
            private String model;

            public Builder content(String content) { this.content = content; return this; }
            public Builder reasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; return this; }
            public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
            public Builder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
            public Builder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
            public Builder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
            public Builder model(String model) { this.model = model; return this; }
            public ChatResult build() {
                ChatResult r = new ChatResult();
                r.content = this.content;
                r.reasoningContent = this.reasoningContent;
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
