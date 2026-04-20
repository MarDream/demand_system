package com.demand.system.module.requirement.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementVO;

import java.util.List;
import java.util.Map;

public interface RequirementService {

    PageResult<RequirementVO> list(RequirementQueryDTO query);

    RequirementVO getDetail(Long id);

    void create(RequirementCreateDTO dto, Long creatorId);

    void update(RequirementUpdateDTO dto, Long userId);

    void delete(Long id, Long userId);

    void restore(Long id, Long userId);

    List<Map<String, Object>> getHistory(Long requirementId);

    List<Map<String, Object>> getChildren(Long parentId);
}
