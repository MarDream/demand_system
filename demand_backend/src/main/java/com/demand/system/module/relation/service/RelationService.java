package com.demand.system.module.relation.service;

import com.demand.system.module.relation.dto.RelationCreateDTO;
import com.demand.system.module.relation.dto.RelationVO;

import java.util.List;

public interface RelationService {

    List<RelationVO> listByRequirement(Long requirementId);

    void create(Long sourceId, RelationCreateDTO dto);

    void delete(Long relationId);

    boolean hasCycle(Long sourceId, Long targetId);
}
