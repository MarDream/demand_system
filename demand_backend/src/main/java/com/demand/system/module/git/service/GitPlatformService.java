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

    GitPlatformVO createPlatform(GitPlatformDTO dto);

    GitPlatformVO updatePlatform(Long id, GitPlatformDTO dto);

    void deletePlatform(Long id);

    Map<String, Object> testConnection(Long id);
}
