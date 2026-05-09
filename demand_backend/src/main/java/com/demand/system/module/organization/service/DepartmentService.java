package com.demand.system.module.organization.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.organization.dto.*;

import java.util.List;

public interface DepartmentService {

    List<DepartmentVO> getTree();

    PageResult<DepartmentVO> list(DepartmentQueryDTO query);

    DepartmentVO getById(Long id);

    void create(DepartmentCreateDTO dto);

    void update(DepartmentUpdateDTO dto);

    void delete(Long id);

    void updateSort(DepartmentSortDTO dto);
}
