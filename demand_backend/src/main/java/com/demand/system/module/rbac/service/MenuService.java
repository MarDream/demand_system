package com.demand.system.module.rbac.service;

import com.demand.system.common.result.Result;
import com.demand.system.module.rbac.dto.MenuCreateDTO;
import com.demand.system.module.rbac.dto.MenuUpdateDTO;
import com.demand.system.module.rbac.dto.MenuSortItem;
import com.demand.system.module.rbac.dto.MenuVO;

import java.util.List;

public interface MenuService {

    Result<List<MenuVO>> listAllMenus();

    Result<List<MenuVO>> listCurrentUserMenus();

    Result<MenuVO> getMenu(Long id);

    Result<Void> createMenu(MenuCreateDTO request);

    Result<Void> updateMenu(MenuUpdateDTO request);

    Result<Void> deleteMenu(Long id);

    Result<Void> batchSort(List<MenuSortItem> items);
}
