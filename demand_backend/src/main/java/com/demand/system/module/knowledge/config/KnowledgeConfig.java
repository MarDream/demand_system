package com.demand.system.module.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeConfig {

    private int chunkSize = 512;
    private int chunkOverlap = 128;
    private int searchTopK = 20;
}
