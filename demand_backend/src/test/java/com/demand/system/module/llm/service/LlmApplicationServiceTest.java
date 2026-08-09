package com.demand.system.module.llm.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.llm.dto.LlmApplicationUpdateDTO;
import com.demand.system.module.llm.dto.LlmApplicationVO;
import com.demand.system.module.llm.entity.LlmApplication;
import com.demand.system.module.llm.entity.LlmModel;
import com.demand.system.module.llm.entity.LlmProvider;
import com.demand.system.module.llm.mapper.LlmApplicationMapper;
import com.demand.system.module.llm.mapper.LlmModelMapper;
import com.demand.system.module.llm.mapper.LlmProviderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmApplicationServiceTest {

    private LlmApplicationMapper applicationMapper;
    private LlmModelMapper modelMapper;
    private LlmProviderMapper providerMapper;
    private LlmApplicationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(LlmApplicationMapper.class);
        modelMapper = mock(LlmModelMapper.class);
        providerMapper = mock(LlmProviderMapper.class);
        service = new LlmApplicationService(applicationMapper, modelMapper, providerMapper);
    }

    @Test
    void visionApplicationAcceptsEnabledVisionModel() {
        LlmApplication application = visionApplication(null);
        LlmModel model = model(10L, "vision", true);
        LlmProvider provider = provider(true);
        LlmApplication updated = visionApplication(10L);

        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(modelMapper.selectById(10L)).thenReturn(model);
        when(applicationMapper.selectById(1L)).thenReturn(updated);
        when(providerMapper.selectById(20L)).thenReturn(provider);

        LlmApplicationUpdateDTO dto = new LlmApplicationUpdateDTO();
        dto.setModelId(10L);
        dto.setEnabled(true);

        LlmApplicationVO result = service.update("knowledge.image-understanding", dto);

        assertEquals("vision", result.getModelType());
        assertEquals(10L, result.getModelId());
        assertTrue(result.getModelAvailable());
        assertEquals("Vision Model", result.getModelName());
        verify(applicationMapper).update(any(), any());
    }

    @Test
    void visionApplicationRejectsNonVisionModel() {
        when(applicationMapper.selectOne(any())).thenReturn(visionApplication(null));
        when(modelMapper.selectById(11L)).thenReturn(model(11L, "general", true));

        LlmApplicationUpdateDTO dto = new LlmApplicationUpdateDTO();
        dto.setModelId(11L);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.update("knowledge.image-understanding", dto)
        );

        assertTrue(error.getMessage().contains("需要 vision 类型模型"));
        verify(applicationMapper, never()).update(any(), any());
    }

    @Test
    void clearingApplicationModelUsesExplicitNullUpdateAndDoesNotFail() {
        LlmApplication application = visionApplication(10L);
        LlmApplication cleared = visionApplication(null);
        cleared.setEnabled(false);
        when(applicationMapper.selectOne(any())).thenReturn(application);
        when(applicationMapper.selectById(1L)).thenReturn(cleared);

        LlmApplicationUpdateDTO dto = new LlmApplicationUpdateDTO();
        dto.setModelId(null);
        dto.setEnabled(false);

        LlmApplicationVO result = service.update("knowledge.image-understanding", dto);

        assertNull(result.getModelId());
        assertFalse(result.getEnabled());
        assertFalse(result.getModelAvailable());
        verify(modelMapper, never()).selectById(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<LlmApplication>> wrapperCaptor =
                ArgumentCaptor.forClass((Class<Wrapper<LlmApplication>>) (Class<?>) Wrapper.class);
        verify(applicationMapper).update(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSet().contains("model_id"));
    }

    private LlmApplication visionApplication(Long modelId) {
        LlmApplication application = new LlmApplication();
        application.setId(1L);
        application.setCode("knowledge.image-understanding");
        application.setName("工单正文图片理解");
        application.setDescription("理解工单正文图片");
        application.setModelType("vision");
        application.setModelId(modelId);
        application.setEnabled(true);
        application.setSortOrder(65);
        return application;
    }

    private LlmModel model(Long id, String modelType, boolean enabled) {
        LlmModel model = new LlmModel();
        model.setId(id);
        model.setProviderId(20L);
        model.setName("Vision Model");
        model.setModelId("vision-model");
        model.setModelType(modelType);
        model.setEnabled(enabled);
        return model;
    }

    private LlmProvider provider(boolean enabled) {
        LlmProvider provider = new LlmProvider();
        provider.setId(20L);
        provider.setName("Vision Provider");
        provider.setEnabled(enabled);
        return provider;
    }
}
