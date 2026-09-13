package com.demand.system.module.llm.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 接入组连通性测试请求（保存前测试，不落库）。
 * <p>apiKey 为空或为打码值（含****）且 providerId 非空时，使用该接入组已保存的 Key（编辑态"不修改请留空"场景）。
 */
public class LlmProviderTestDTO {

    /** 编辑已有接入组时传入，用于回填已保存的 API Key。 */
    private Long providerId;

    @NotBlank(message = "协议类型不能为空")
    private String protocol;

    @NotBlank(message = "API Base URL不能为空")
    private String baseUrl;

    private String apiKey;

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
