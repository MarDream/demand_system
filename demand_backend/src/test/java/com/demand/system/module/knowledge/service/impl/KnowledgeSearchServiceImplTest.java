package com.demand.system.module.knowledge.service.impl;

import com.demand.system.module.knowledge.config.KnowledgeConfig;
import com.demand.system.module.knowledge.dto.KnowledgeSearchRequest;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;
import com.demand.system.module.knowledge.mapper.KnowledgeChunkMapper;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import com.demand.system.module.knowledge.service.EmbeddingService;
import com.demand.system.module.knowledge.service.ImageUnderstandingService;
import com.demand.system.module.knowledge.service.IntentRecognizer;
import com.demand.system.module.knowledge.service.RagAnswerService;
import com.demand.system.module.knowledge.vectorstore.MilvusVectorStore;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.requirement.service.RequirementService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KnowledgeSearchServiceImplTest {

    @Test
    void explicitInvalidScopesDoNotFallbackToAllLocalSources() {
        KnowledgeSearchServiceImpl service = new KnowledgeSearchServiceImpl(
                mock(EmbeddingService.class),
                mock(MilvusVectorStore.class),
                mock(KnowledgeConfig.class),
                mock(RagAnswerService.class),
                mock(IntentRecognizer.class),
                mock(RequirementMapper.class),
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeChunkMapper.class),
                mock(RequirementService.class),
                disabledImageUnderstandingService());

        KnowledgeSearchRequest request = new KnowledgeSearchRequest();
        request.setQuery("只允许有效范围");
        request.setMode("keyword");
        request.setSearchScopes(List.of("INVALID"));

        KnowledgeSearchResponse response = service.search(request);

        assertTrue(response.getResults().isEmpty());
        assertTrue(response.getCitations().isEmpty());
    }

    private ImageUnderstandingService disabledImageUnderstandingService() {
        return new ImageUnderstandingService() {
            @Override
            public com.demand.system.module.knowledge.service.ImageUnderstandingResult analyze(
                    byte[] imageBytes, String contentType, String altText) {
                return com.demand.system.module.knowledge.service.ImageUnderstandingResult.empty();
            }

            @Override
            public boolean enabled() {
                return false;
            }
        };
    }
}
