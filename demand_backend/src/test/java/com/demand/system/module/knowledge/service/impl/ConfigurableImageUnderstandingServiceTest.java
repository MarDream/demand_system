package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.demand.system.module.knowledge.service.ImageUnderstandingResult;
import com.demand.system.module.llm.entity.LlmApplication;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurableImageUnderstandingServiceTest {

    @Test
    void returnsEmptyResultWhenModelApplicationIsNotConfigured() {
        LlmGateway gateway = mock(LlmGateway.class);
        LlmModelResolver resolver = mock(LlmModelResolver.class);
        when(resolver.resolveFirst(anyString())).thenReturn(null);
        ConfigurableImageUnderstandingService service = new ConfigurableImageUnderstandingService(
                gateway, resolver, new ObjectMapper());

        ImageUnderstandingResult result = service.analyze(new byte[]{1, 2, 3}, "image/png", "错误截图");

        assertFalse(service.enabled());
        assertEquals("模型应用不存在、未启用或没有可用模型", service.unavailableReason());
        assertFalse(result.hasContent());
        verify(gateway, never()).chatWithImageWithProvider(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void parsesJsonInsideMarkdownFence() {
        LlmGateway gateway = mock(LlmGateway.class);
        LlmModelResolver resolver = mock(LlmModelResolver.class);
        LlmModelResolver.ResolvedModel resolved = configuredModel();
        when(resolver.resolveFirst(anyString())).thenReturn(resolved);
        when(resolver.toGatewayProvider(resolved)).thenReturn(configuredProvider());
        when(gateway.chatWithImageWithProvider(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(LlmGateway.ChatResult.builder()
                        .content("```json\n{\"ocrText\":\"HTTP 500\",\"caption\":\"登录页面显示服务异常\"}\n```")
                        .build());
        ConfigurableImageUnderstandingService service = new ConfigurableImageUnderstandingService(
                gateway, resolver, new ObjectMapper());

        ImageUnderstandingResult result = service.analyze(new byte[]{1}, "image/png", "错误截图");

        assertTrue(result.hasContent());
        assertEquals("HTTP 500", result.ocrText());
        assertEquals("登录页面显示服务异常", result.caption());
    }

    @Test
    void extractsJsonFromAdditionalNaturalLanguage() {
        LlmGateway gateway = mock(LlmGateway.class);
        LlmModelResolver resolver = mock(LlmModelResolver.class);
        LlmModelResolver.ResolvedModel resolved = configuredModel();
        when(resolver.resolveFirst(anyString())).thenReturn(resolved);
        when(resolver.toGatewayProvider(resolved)).thenReturn(configuredProvider());
        when(gateway.chatWithImageWithProvider(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(LlmGateway.ChatResult.builder()
                        .content("分析结果如下：{\"ocrText\":\"数据库连接超时\",\"caption\":\"监控告警截图\"} 请查收")
                        .build());
        ConfigurableImageUnderstandingService service = new ConfigurableImageUnderstandingService(
                gateway, resolver, new ObjectMapper());

        ImageUnderstandingResult result = service.analyze(new byte[]{1}, "image/jpeg", "告警");

        assertEquals("数据库连接超时", result.ocrText());
        assertEquals("监控告警截图", result.caption());
    }

    @Test
    void degradesToEmptyResultWhenProviderCallFails() {
        LlmGateway gateway = mock(LlmGateway.class);
        LlmModelResolver resolver = mock(LlmModelResolver.class);
        LlmModelResolver.ResolvedModel resolved = configuredModel();
        when(resolver.resolveFirst(anyString())).thenReturn(resolved);
        when(resolver.toGatewayProvider(resolved)).thenReturn(configuredProvider());
        when(gateway.chatWithImageWithProvider(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("provider unavailable"));
        ConfigurableImageUnderstandingService service = new ConfigurableImageUnderstandingService(
                gateway, resolver, new ObjectMapper());

        ImageUnderstandingResult result = service.analyze(new byte[]{1}, "image/webp", null);

        assertFalse(result.hasContent());
    }

    @Test
    void doesNotUseFallbackTextModelWhenApplicationHasNoExplicitModel() {
        LlmGateway gateway = mock(LlmGateway.class);
        LlmModelResolver resolver = mock(LlmModelResolver.class);
        LlmApplication application = new LlmApplication();
        application.setEnabled(true);
        application.setModelId(null);
        LlmModel model = new LlmModel();
        model.setId(10L);
        LlmProvider provider = new LlmProvider();
        LlmModelResolver.ResolvedModel resolved = new LlmModelResolver.ResolvedModel(application, model, provider);
        when(resolver.resolveFirst(anyString())).thenReturn(resolved);
        ConfigurableImageUnderstandingService service = new ConfigurableImageUnderstandingService(
                gateway, resolver, new ObjectMapper());

        assertFalse(service.enabled());
        assertEquals("模型应用未选择图片理解模型", service.unavailableReason());
        assertFalse(service.analyze(new byte[]{1}, "image/png", null).hasContent());
        verify(gateway, never()).chatWithImageWithProvider(any(), anyString(), anyString(), anyString(), any());
    }

    private LlmModelResolver.ResolvedModel configuredModel() {
        LlmApplication application = new LlmApplication();
        application.setEnabled(true);
        application.setModelId(10L);
        LlmModel model = new LlmModel();
        model.setId(10L);
        model.setModelId("vision-model");
        model.setModelType("vision");
        LlmProvider provider = new LlmProvider();
        provider.setProtocol("openai");
        provider.setBaseUrl("https://vision.example.test/v1");
        provider.setApiKey("test-key");
        provider.setEnabled(true);
        return new LlmModelResolver.ResolvedModel(application, model, provider);
    }

    private LlmGatewayConfig.Provider configuredProvider() {
        LlmGatewayConfig.Provider provider = new LlmGatewayConfig.Provider();
        provider.setProtocol("openai");
        provider.setBaseUrl("https://vision.example.test/v1");
        provider.setApiKey("test-key");
        provider.setModel("vision-model");
        return provider;
    }
}