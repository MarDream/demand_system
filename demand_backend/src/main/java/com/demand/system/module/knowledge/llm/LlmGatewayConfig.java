package com.demand.system.module.knowledge.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmGatewayConfig {

    private Provider embedding = new Provider();
    private Provider reranker = new Provider();
    private Provider chat = new Provider();
    /** Provider for event/entity extraction during multi-hop expansion */
    private Provider eventExtractor = new Provider();
    /** Provider for LLM-based rerank (standard mode) */
    private Provider llmReranker = new Provider();

    public Provider getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Provider embedding) {
        this.embedding = embedding;
    }

    public Provider getReranker() {
        return reranker;
    }

    public void setReranker(Provider reranker) {
        this.reranker = reranker;
    }

    public Provider getChat() {
        return chat;
    }

    public void setChat(Provider chat) {
        this.chat = chat;
    }

    public Provider getEventExtractor() {
        return eventExtractor;
    }

    public void setEventExtractor(Provider eventExtractor) {
        this.eventExtractor = eventExtractor;
    }

    public Provider getLlmReranker() {
        return llmReranker;
    }

    public void setLlmReranker(Provider llmReranker) {
        this.llmReranker = llmReranker;
    }

    public static class Provider {
        private String protocol = "openai";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String dimension = null;

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getDimension() {
            return dimension;
        }

        public void setDimension(String dimension) {
            this.dimension = dimension;
        }
    }

    public enum Protocol {
        OPENAI,
        ANTHROPIC
    }

    public Protocol resolveProtocol(Provider provider) {
        return "anthropic".equalsIgnoreCase(provider.getProtocol()) ? Protocol.ANTHROPIC : Protocol.OPENAI;
    }
}
