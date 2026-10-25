package com.demand.system.module.git.service;

import com.demand.system.module.git.dto.GitRepositoryDTO;
import com.demand.system.module.git.dto.GitRepositoryVO;

import java.util.List;

public interface GitRepositoryService {

    /**
     * 仓库列表（支持 keyword/platformId/projectId/status 筛选）
     */
    List<GitRepositoryVO> listRepositories(String keyword, Long platformId, Long projectId, String status);

    GitRepositoryVO createRepository(GitRepositoryDTO dto);

    GitRepositoryVO updateRepository(Long id, GitRepositoryDTO dto);

    GitRepositoryVO getRepositoryDetail(Long id);

    /**
     * 归档仓库（status -> archived）
     */
    void archiveRepository(Long id);

    void deleteRepository(Long id);
}
