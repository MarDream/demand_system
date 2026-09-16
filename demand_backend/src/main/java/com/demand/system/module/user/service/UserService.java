package com.demand.system.module.user.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;

import java.util.List;
import java.util.Map;

public interface UserService {

    PageResult<UserVO> list(UserQueryDTO query);

    List<Map<String, Object>> listActiveUsers();

    UserVO getById(Long id);

    Long create(UserCreateDTO dto);

    void update(UserUpdateDTO dto);

    void delete(Long id);

    /**
     * 批量启用/停用。
     *
     * @param ids    目标用户ID集合
     * @param status 目标状态：active=启用, inactive=停用
     * @return 实际生效的用户数
     */
    int batchUpdateStatus(List<Long> ids, String status);

    /**
     * 批量删除。
     *
     * @param ids 目标用户ID集合
     * @return 实际删除的用户数
     */
    int batchDelete(List<Long> ids);

    boolean resetInitialPassword(Long id);

    void assignRoles(Long userId, List<Long> roleIds);

    List<Long> getUserRoleIds(Long userId);
}
