package com.demand.system.module.onlyoffice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeConfig {

    private String serverUrl = "http://localhost:8443";

    private String jwtSecret;

    private String callbackUrl;

    private String documentAccessEndpoint;
}
