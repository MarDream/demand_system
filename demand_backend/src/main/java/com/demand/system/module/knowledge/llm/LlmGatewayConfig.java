package com.demand.system.module.knowledge.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmGatewayConfig {

    private Provider embedding = new Provider();
    private Provider reranker = new Provider();
    private Provider chat = new Provider();

    @Data
    public static class Provider {
        private String protocol = "openai";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String dimension = "1024";
    }

    public enum Protocol {
        OPENAI,
        ANTHROPIC
    }

    public Protocol resolveProtocol(Provider provider) {
        return "anthropic".equalsIgnoreCase(provider.getProtocol()) ? Protocol.ANTHROPIC : Protocol.OPENAI;
    }
}
