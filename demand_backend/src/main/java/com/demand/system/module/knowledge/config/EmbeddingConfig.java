package com.demand.system.module.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingConfig {

    private String apiUrl = "http://localhost:8100";
    private String model = "bge-m3";
    private String apiKey = "";
    private int dimension = 1024;
}
