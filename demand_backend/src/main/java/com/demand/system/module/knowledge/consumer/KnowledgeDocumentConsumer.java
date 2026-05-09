package com.demand.system.module.knowledge.consumer;

import com.demand.system.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentConsumer {

    private final KnowledgeDocumentService documentService;

    @RabbitListener(queues = "knowledge.document.process.queue")
    public void handleDocumentProcess(Long documentId) {
        log.info("收到文档处理任务: documentId={}", documentId);
        try {
            documentService.processDocument(documentId);
            log.info("文档处理完成: documentId={}", documentId);
        } catch (Exception e) {
            log.error("文档处理失败: documentId={}", documentId, e);
        }
    }
}
