package com.demand.system.module.git.service;

import com.demand.system.module.git.dto.GitPlatformDTO;
import com.demand.system.module.git.dto.GitPlatformVO;

import java.util.List;
import java.util.Map;

public interface GitPlatformService {

    /**
     * 平台列表（默认平台排前）
     */
    List<GitPlatformVO> listPlatforms();

    /**
     * 创建平台
     */
    GitPlatformVO createPlatform(GitPlatformDTO dto);

    /**
     * 更新平台
     */
    GitPlatformVO updatePlatform(Long id, GitPlatformDTO dto);

    /**
     * 删除平台
     */
    void deletePlatform(Long id);

    /**
     * 测试平台连接
     */
    Map<String, Object> testConnection(Long id);
}
