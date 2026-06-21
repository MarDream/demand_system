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

    boolean resetInitialPassword(Long id);

    void assignRoles(Long userId, List<Long> roleIds);

    List<Long> getUserRoleIds(Long userId);
}
