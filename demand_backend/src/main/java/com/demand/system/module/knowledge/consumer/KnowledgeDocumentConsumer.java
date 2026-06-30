package com.demand.system.module.knowledge.consumer;

import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentConsumer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeDocumentConsumer.class);

    private final KnowledgeDocumentService documentService;

    public KnowledgeDocumentConsumer(KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @RabbitListener(queues = "knowledge.document.process.queue")
    public void handleDocumentProcess(Long documentId) {
        log.info("收到文档处理任务: documentId={}", documentId);
        documentService.processDocument(documentId);
        log.info("文档处理完成: documentId={}", documentId);
    }
}
