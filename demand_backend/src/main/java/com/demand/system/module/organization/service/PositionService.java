package com.demand.system.module.organization.service;

import com.demand.system.module.organization.dto.PositionCreateDTO;
import com.demand.system.module.organization.dto.PositionSortDTO;
import com.demand.system.module.organization.dto.PositionUpdateDTO;
import com.demand.system.module.organization.dto.PositionVO;

import java.util.List;

public interface PositionService {

    List<PositionVO> list();

    PositionVO getById(Long id);

    void create(PositionCreateDTO dto);

    void update(PositionUpdateDTO dto);

    void delete(Long id);

    void updateSort(PositionSortDTO dto);
}
