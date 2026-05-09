package com.demand.system.module.organization.service;

import com.demand.system.module.organization.dto.RegionCreateDTO;
import com.demand.system.module.organization.dto.RegionSortDTO;
import com.demand.system.module.organization.dto.RegionUpdateDTO;
import com.demand.system.module.organization.dto.RegionVO;

import java.util.List;

public interface RegionService {

    List<RegionVO> getTree();

    RegionVO getById(Long id);

    void create(RegionCreateDTO dto);

    void update(RegionUpdateDTO dto);

    void delete(Long id);

    void updateSort(RegionSortDTO dto);
}
