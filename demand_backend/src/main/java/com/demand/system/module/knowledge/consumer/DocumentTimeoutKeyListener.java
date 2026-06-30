package com.demand.system.module.knowledge.consumer;

import com.demand.system.module.knowledge.entity.KnowledgeDocument;
import com.demand.system.module.knowledge.mapper.KnowledgeDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 过期事件监听器 — 文档处理超时检测
 *
 * 当 demand:doc:timeout:{docId} 的 Redis key 过期时，
 * 本监听器收到事件，检查对应文档是否仍处于处理中状态，
 * 若是则自动标记为 failed。
 *
 * 优势（相比原 KnowledgeDocumentTimeoutChecker）：
 * - 零轮询：无文档处理时完全不消耗数据库资源
 * - 精确超时：key 过期即触发，无 5 分钟延迟窗口
 * - 知识库个性超时：每个知识库可独立配置超时时间
 */
@Component
public class DocumentTimeoutKeyListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentTimeoutKeyListener.class);
    private static final String KEY_PREFIX = "demand:doc:timeout:";
    private static final List<String> PROCESSING_STATES = List.of("pending", "parsed", "indexing");

    private final KnowledgeDocumentMapper documentMapper;

    public DocumentTimeoutKeyListener(KnowledgeDocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody());
        if (!expiredKey.startsWith(KEY_PREFIX)) {
            return;
        }

        Long docId;
        try {
            docId = Long.parseLong(expiredKey.substring(KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("无法解析过期 key 中的文档ID: {}", expiredKey);
            return;
        }

        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            log.debug("过期 key 对应的文档不存在，忽略: docId={}", docId);
            return;
        }

        if (!PROCESSING_STATES.contains(doc.getStatus())) {
            log.debug("文档已不在处理中状态，忽略超时事件: docId={}, status={}", docId, doc.getStatus());
            return;
        }

        String originalStatus = doc.getStatus();
        doc.setStatus("failed");
        doc.setErrorMessage("处理超时：文档未在规定时间内完成处理");
        documentMapper.updateById(doc);
        log.warn("文档超时标记(Redis过期事件驱动): id={}, name={}, 原状态={}", docId, doc.getFileName(), originalStatus);
    }
}
