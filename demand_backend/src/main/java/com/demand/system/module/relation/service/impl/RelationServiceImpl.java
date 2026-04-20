package com.demand.system.module.relation.service.impl;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.relation.dto.RelationCreateDTO;
import com.demand.system.module.relation.dto.RelationVO;
import com.demand.system.module.relation.entity.RequirementRelation;
import com.demand.system.module.requirement.mapper.RequirementMapper;
import com.demand.system.module.relation.mapper.RequirementRelationMapper;
import com.demand.system.module.relation.service.RelationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private final RequirementRelationMapper relationMapper;
    private final RequirementMapper requirementMapper;

    @Override
    public List<RelationVO> listByRequirement(Long requirementId) {
        List<Map<String, Object>> rows = relationMapper.selectWithTarget(requirementId);
        List<RelationVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            RelationVO vo = new RelationVO();
            vo.setId(getLong(row, "id"));
            vo.setSourceId(getLong(row, "source_id"));
            vo.setTargetId(getLong(row, "target_id"));
            vo.setRelationType((String) row.get("relation_type"));
            vo.setTargetTitle((String) row.get("target_title"));
            vo.setTargetStatus((String) row.get("target_status"));
            vo.setTargetPriority((String) row.get("target_priority"));
            result.add(vo);
        }
        return result;
    }

    @Override
    public void create(Long sourceId, RelationCreateDTO dto) {
        String relationType = dto.getRelationType();
        Long targetId = dto.getTargetId();

        // 1. Check not duplicate
        int count = relationMapper.exists(sourceId, targetId, relationType);
        if (count > 0) {
            throw new BusinessException("关联关系已存在");
        }

        // 2. Cycle check for DEPENDENCY and PARENT_CHILD
        if ("DEPENDENCY".equals(relationType) || "PARENT_CHILD".equals(relationType)) {
            if (hasCycle(sourceId, targetId)) {
                throw new BusinessException("创建关联将导致循环依赖");
            }
        }

        // 3. Insert
        RequirementRelation relation = new RequirementRelation();
        relation.setSourceId(sourceId);
        relation.setTargetId(targetId);
        relation.setRelationType(relationType);
        relationMapper.insert(relation);
    }

    @Override
    public void delete(Long relationId) {
        relationMapper.deleteById(relationId);
    }

    @Override
    public boolean hasCycle(Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            return true;
        }

        // Build adjacency map from all requirement_relations
        List<RequirementRelation> allRelations = relationMapper.selectList(null);
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (RequirementRelation rel : allRelations) {
            adjacency.computeIfAbsent(rel.getSourceId(), k -> new ArrayList<>()).add(rel.getTargetId());
        }

        // BFS from targetId to see if we can reach sourceId
        Queue<Long> queue = new LinkedList<>();
        queue.offer(targetId);
        Set<Long> visited = new HashSet<>();
        visited.add(targetId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(sourceId)) {
                return true;
            }
            List<Long> neighbors = adjacency.get(current);
            if (neighbors != null) {
                for (Long neighbor : neighbors) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }
        }

        return false;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        return ((Number) val).longValue();
    }
}
