package com.demand.system.module.iteration.service;

import com.demand.system.module.iteration.dto.IterationCreateDTO;
import com.demand.system.module.iteration.dto.IterationUpdateDTO;
import com.demand.system.module.iteration.dto.IterationVO;

import com.demand.system.common.result.PageResult;

import java.util.List;
import java.util.Map;

public interface IterationService {

    PageResult<IterationVO> listByProject(Long projectId, int pageNum, int pageSize);

    IterationVO getById(Long id);

    void create(IterationCreateDTO dto, Long userId);

    void update(IterationUpdateDTO dto);

    void delete(Long id);

    void assignRequirements(Long iterationId, List<Long> requirementIds);

    Map<String, Object> getBurndownData(Long iterationId);
}
