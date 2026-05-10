package com.demand.system.module.knowledge.config;

import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentBackfillRunner implements CommandLineRunner {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @Override
    public void run(String... args) {
        try {
            int updatedCount = knowledgeDocumentService.backfillDocumentMetadata();
            log.info("Knowledge document metadata backfill finished, updated {} records", updatedCount);
        } catch (Exception e) {
            log.warn("Knowledge document metadata backfill failed", e);
        }
    }
}
