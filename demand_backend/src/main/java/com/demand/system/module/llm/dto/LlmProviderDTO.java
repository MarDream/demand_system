package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.*;

public class LlmProviderDTO {
    @NotBlank(message = "名称不能为空")
    private String name;
    @NotBlank(message = "协议类型不能为空")
    private String protocol;
    @NotBlank(message = "API Base URL不能为空")
    private String baseUrl;
    private String apiKey;
    private Boolean enabled = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
