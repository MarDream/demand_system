package com.demand.system.module.knowledge.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.knowledge.dto.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeDocumentService {

    KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long uploaderId);

    PageResult<KnowledgeDocumentVO> list(Long knowledgeBaseId, int pageNum, int pageSize);

    void delete(Long knowledgeBaseId, Long documentId);

    void processDocument(Long documentId);
}
