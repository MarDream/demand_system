package com.demand.system.module.knowledge.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.knowledge.dto.KnowledgeBaseCreateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseUpdateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseVO;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto, Long creatorId);

    KnowledgeBaseVO getById(Long id);

    PageResult<KnowledgeBaseVO> list(String name, int pageNum, int pageSize);

    KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateDTO dto);

    void delete(Long id);

    List<KnowledgeBaseVO> listAll();
}
