package com.demand.system.module.knowledge.config;

import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentBackfillRunner {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int updatedCount = knowledgeDocumentService.backfillDocumentMetadata();
            log.info("Knowledge document metadata backfill finished, updated {} records", updatedCount);
        } catch (Exception e) {
            log.warn("Knowledge document metadata backfill failed", e);
        }
    }
}
