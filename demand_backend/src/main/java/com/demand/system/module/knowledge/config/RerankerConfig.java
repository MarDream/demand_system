package com.demand.system.module.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "reranker")
public class RerankerConfig {

    private String apiUrl = "http://localhost:8100";
    private String model = "bge-reranker-v2-m3";
}
