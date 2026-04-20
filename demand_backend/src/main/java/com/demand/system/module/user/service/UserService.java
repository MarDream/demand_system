package com.demand.system.module.user.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.user.dto.UserCreateDTO;
import com.demand.system.module.user.dto.UserQueryDTO;
import com.demand.system.module.user.dto.UserUpdateDTO;
import com.demand.system.module.user.dto.UserVO;

public interface UserService {

    PageResult<UserVO> list(UserQueryDTO query);

    UserVO getById(Long id);

    void create(UserCreateDTO dto);

    void update(UserUpdateDTO dto);

    void delete(Long id);
}
