package com.demand.system.module.knowledge.config;

import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentBackfillRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentBackfillRunner.class);

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentBackfillRunner(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int updatedCount = knowledgeDocumentService.backfillDocumentMetadata();
            log.info("Knowledge document metadata backfill finished, updated {} records", updatedCount);

            int retriedCount = knowledgeDocumentService.retryRecoverableFailedDocuments();
            if (retriedCount > 0) {
                log.warn("已自动重新提交 {} 个因向量维度不匹配或临时存储连接故障而失败的知识库文档", retriedCount);
            }
        } catch (Exception e) {
            log.warn("Knowledge document metadata backfill failed", e);
        }
    }
}
