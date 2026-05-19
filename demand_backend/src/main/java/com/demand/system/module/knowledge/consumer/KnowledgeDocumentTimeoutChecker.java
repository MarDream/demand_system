package com.demand.system.module.knowledge.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class KnowledgeDocumentTimeoutChecker {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentTimeoutChecker.class);
    private static final int TIMEOUT_MINUTES = 20;
    private static final List<String> PROCESSING_STATES = List.of("pending", "parsed", "indexing");

    private final KnowledgeDocumentMapper documentMapper;

    public KnowledgeDocumentTimeoutChecker(KnowledgeDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Scheduled(fixedRate = 300000)
    public void checkTimeout() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<KnowledgeDocument> stuck = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .in(KnowledgeDocument::getStatus, PROCESSING_STATES)
                        .lt(KnowledgeDocument::getUpdatedAt, threshold)
        );
        if (stuck.isEmpty()) return;

        log.warn("发现 {} 个超时文档，自动标记为失败", stuck.size());
        for (KnowledgeDocument doc : stuck) {
            doc.setStatus("failed");
            doc.setErrorMessage("处理超时：文档在 " + TIMEOUT_MINUTES + " 分钟内未完成处理");
            documentMapper.updateById(doc);
            log.info("文档超时标记: id={}, name={}, 原状态={}", doc.getId(), doc.getFileName(), doc.getStatus());
        }
    }
}
