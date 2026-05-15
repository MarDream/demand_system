package com.demand.system.module.organization.service;

import com.demand.system.module.organization.dto.*;

import java.util.List;

public interface SysOrgService {

    List<SysOrgVO> getTree();

    SysOrgVO getDetail(Long id);

    void create(SysOrgCreateDTO dto);

    void update(SysOrgUpdateDTO dto);

    void delete(Long id);

    void move(SysOrgMoveDTO dto);

    List<Long> getDescendantIds(Long orgId);
}
