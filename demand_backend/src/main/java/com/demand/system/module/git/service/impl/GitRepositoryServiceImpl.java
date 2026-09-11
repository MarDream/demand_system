package com.demand.system.module.git.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.security.PermissionGuard;
import com.demand.system.module.git.dto.GitRepositoryDTO;
import com.demand.system.module.git.dto.GitRepositoryVO;
import com.demand.system.module.git.entity.GitPlatform;
import com.demand.system.module.git.entity.GitRepository;
import com.demand.system.module.git.mapper.GitPlatformMapper;
import com.demand.system.module.git.mapper.GitRepositoryMapper;
import com.demand.system.module.git.service.GitAuditService;
import com.demand.system.module.git.service.GitRepositoryService;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GitRepositoryServiceImpl implements GitRepositoryService {

    private final GitRepositoryMapper gitRepositoryMapper;
    private final GitPlatformMapper gitPlatformMapper;
    private final ProjectMapper projectMapper;
    private final GitAuditService gitAuditService;
    private final ObjectMapper objectMapper;

    public GitRepositoryServiceImpl(GitRepositoryMapper gitRepositoryMapper, GitPlatformMapper gitPlatformMapper,
                                    ProjectMapper projectMapper, GitAuditService gitAuditService,
                                    ObjectMapper objectMapper) {
        this.gitRepositoryMapper = gitRepositoryMapper;
        this.gitPlatformMapper = gitPlatformMapper;
        this.projectMapper = projectMapper;
        this.gitAuditService = gitAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GitRepositoryVO> listRepositories(String keyword, Long platformId, Long projectId, String status) {
        LambdaQueryWrapper<GitRepository> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(GitRepository::getName, keyword)
                    .or().like(GitRepository::getFullPath, keyword));
        }
        if (platformId != null) {
            wrapper.eq(GitRepository::getPlatformId, platformId);
        }
        if (projectId != null) {
            wrapper.eq(GitRepository::getProjectId, projectId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(GitRepository::getStatus, status);
        }
        wrapper.orderByDesc(GitRepository::getId);

        return gitRepositoryMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public GitRepositoryVO createRepository(GitRepositoryDTO dto) {
        GitPlatform platform = gitPlatformMapper.selectById(dto.getPlatformId());
        if (platform == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 平台不存在: " + dto.getPlatformId());
        }
        if (dto.getProjectId() != null && projectMapper.selectById(dto.getProjectId()) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关联项目不存在: " + dto.getProjectId());
        }
        GitRepository repo = new GitRepository();
        repo.setPlatformId(dto.getPlatformId());
        repo.setProjectId(dto.getProjectId());
        repo.setName(dto.getName().trim());
        repo.setFullPath(dto.getFullPath());
        repo.setDescription(dto.getDescription());
        repo.setDefaultBranch(StringUtils.hasText(dto.getDefaultBranch()) ? dto.getDefaultBranch() : "main");
        repo.setCloneUrlSsh(dto.getCloneUrlSsh());
        repo.setCloneUrlHttps(dto.getCloneUrlHttps());
        repo.setRemoteId(dto.getRemoteId());
        repo.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "active");
        repo.setCreatedBy(PermissionGuard.requireCurrentUserId());
        gitRepositoryMapper.insert(repo);

        gitAuditService.record("create", "git_repository", repo.getId(), repo.getName(),
                toJson(buildAuditDetail(repo)));
        return toVO(repo);
    }

    @Override
    @Transactional
    public GitRepositoryVO updateRepository(Long id, GitRepositoryDTO dto) {
        GitRepository repo = requireRepository(id);
        if (dto.getPlatformId() != null) {
            GitPlatform platform = gitPlatformMapper.selectById(dto.getPlatformId());
            if (platform == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Git 平台不存在: " + dto.getPlatformId());
            }
            repo.setPlatformId(dto.getPlatformId());
        }
        if (dto.getProjectId() != null) {
            if (projectMapper.selectById(dto.getProjectId()) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关联项目不存在: " + dto.getProjectId());
            }
            repo.setProjectId(dto.getProjectId());
        }
        if (StringUtils.hasText(dto.getName())) {
            repo.setName(dto.getName().trim());
        }
        if (dto.getFullPath() != null) {
            repo.setFullPath(dto.getFullPath());
        }
        if (dto.getDescription() != null) {
            repo.setDescription(dto.getDescription());
        }
        if (StringUtils.hasText(dto.getDefaultBranch())) {
            repo.setDefaultBranch(dto.getDefaultBranch());
        }
        if (dto.getCloneUrlSsh() != null) {
            repo.setCloneUrlSsh(dto.getCloneUrlSsh());
        }
        if (dto.getCloneUrlHttps() != null) {
            repo.setCloneUrlHttps(dto.getCloneUrlHttps());
        }
        if (dto.getRemoteId() != null) {
            repo.setRemoteId(dto.getRemoteId());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            repo.setStatus(dto.getStatus());
        }
        repo.setUpdatedAt(LocalDateTime.now());
        gitRepositoryMapper.updateById(repo);

        gitAuditService.record("update", "git_repository", repo.getId(), repo.getName(),
                toJson(buildAuditDetail(repo)));
        return toVO(repo);
    }

    @Override
    public GitRepositoryVO getRepositoryDetail(Long id) {
        return toVO(requireRepository(id));
    }

    @Override
    @Transactional
    public void archiveRepository(Long id) {
        GitRepository repo = requireRepository(id);
        repo.setStatus("archived");
        repo.setUpdatedAt(LocalDateTime.now());
        gitRepositoryMapper.updateById(repo);
        gitAuditService.record("archive", "git_repository", repo.getId(), repo.getName(),
                toJson(buildAuditDetail(repo)));
    }

    @Override
    @Transactional
    public void deleteRepository(Long id) {
        GitRepository repo = requireRepository(id);
        gitRepositoryMapper.deleteById(id);
        gitAuditService.record("delete", "git_repository", repo.getId(), repo.getName(),
                toJson(buildAuditDetail(repo)));
    }

    private GitRepository requireRepository(Long id) {
        GitRepository repo = gitRepositoryMapper.selectById(id);
        if (repo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在: " + id);
        }
        return repo;
    }

    private GitRepositoryVO toVO(GitRepository repo) {
        GitRepositoryVO vo = new GitRepositoryVO();
        vo.setId(repo.getId());
        vo.setPlatformId(repo.getPlatformId());
        vo.setPlatformName(resolvePlatformName(repo.getPlatformId()));
        vo.setProjectId(repo.getProjectId());
        vo.setProjectName(resolveProjectName(repo.getProjectId()));
        vo.setName(repo.getName());
        vo.setFullPath(repo.getFullPath());
        vo.setDescription(repo.getDescription());
        vo.setDefaultBranch(repo.getDefaultBranch());
        vo.setCloneUrlSsh(repo.getCloneUrlSsh());
        vo.setCloneUrlHttps(repo.getCloneUrlHttps());
        vo.setRemoteId(repo.getRemoteId());
        vo.setStatus(repo.getStatus());
        vo.setCreatedBy(repo.getCreatedBy());
        vo.setCreatedAt(repo.getCreatedAt());
        vo.setUpdatedAt(repo.getUpdatedAt());
        return vo;
    }

    private String resolvePlatformName(Long platformId) {
        if (platformId == null) {
            return null;
        }
        GitPlatform platform = gitPlatformMapper.selectById(platformId);
        return platform != null ? platform.getName() : null;
    }

    private String resolveProjectName(Long projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null ? project.getName() : null;
    }

    private Map<String, Object> buildAuditDetail(GitRepository repo) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", repo.getName());
        detail.put("fullPath", repo.getFullPath());
        detail.put("platformId", repo.getPlatformId());
        detail.put("projectId", repo.getProjectId());
        detail.put("defaultBranch", repo.getDefaultBranch());
        detail.put("status", repo.getStatus());
        return detail;
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }
}
