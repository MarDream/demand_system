package com.demand.system.module.git.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.security.PermissionGuard;
import com.demand.system.module.git.dto.GitPlatformDTO;
import com.demand.system.module.git.dto.GitPlatformVO;
import com.demand.system.module.git.entity.GitPlatform;
import com.demand.system.module.git.mapper.GitPlatformMapper;
import com.demand.system.module.git.service.GitAuditService;
import com.demand.system.module.git.service.GitPlatformService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GitPlatformServiceImpl implements GitPlatformService {

    private static final String CREDENTIAL_MASK = "******";

    private final GitPlatformMapper gitPlatformMapper;
    private final GitAuditService gitAuditService;
    private final ObjectMapper objectMapper;

    public GitPlatformServiceImpl(GitPlatformMapper gitPlatformMapper, GitAuditService gitAuditService,
                                  ObjectMapper objectMapper) {
        this.gitPlatformMapper = gitPlatformMapper;
        this.gitAuditService = gitAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<GitPlatformVO> listPlatforms() {
        return gitPlatformMapper.selectList(new LambdaQueryWrapper<GitPlatform>()
                        .orderByDesc(GitPlatform::getIsDefault)
                        .orderByDesc(GitPlatform::getCreatedAt))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public GitPlatformVO createPlatform(GitPlatformDTO dto) {
        GitPlatform platform = new GitPlatform();
        platform.setName(dto.getName().trim());
        platform.setPlatformType(dto.getPlatformType());
        platform.setBaseUrl(dto.getBaseUrl().trim());
        platform.setAuthType(dto.getAuthType());
        platform.setCredential(dto.getCredential());
        platform.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 0);
        platform.setStatus(dto.getStatus() != null ? dto.getStatus() : "enabled");
        gitPlatformMapper.insert(platform);
        if (platform.getIsDefault() == 1) {
            clearOtherDefault(platform.getId());
        }
        gitAuditService.record("create", "git_platform", platform.getId(), platform.getName(),
                toJson(buildAuditDetail(platform)));
        return toVO(platform);
    }

    @Override
    @Transactional
    public GitPlatformVO updatePlatform(Long id, GitPlatformDTO dto) {
        GitPlatform platform = requirePlatform(id);
        if (StringUtils.hasText(dto.getName())) {
            platform.setName(dto.getName().trim());
        }
        if (StringUtils.hasText(dto.getPlatformType())) {
            platform.setPlatformType(dto.getPlatformType());
        }
        if (StringUtils.hasText(dto.getBaseUrl())) {
            platform.setBaseUrl(dto.getBaseUrl().trim());
        }
        if (dto.getAuthType() != null) {
            platform.setAuthType(dto.getAuthType());
        }
        // 凭据留空表示不修改
        if (StringUtils.hasText(dto.getCredential())) {
            platform.setCredential(dto.getCredential());
        }
        if (dto.getIsDefault() != null) {
            platform.setIsDefault(dto.getIsDefault());
        }
        if (StringUtils.hasText(dto.getStatus())) {
            platform.setStatus(dto.getStatus());
        }
        platform.setUpdatedAt(LocalDateTime.now());
        gitPlatformMapper.updateById(platform);
        if (platform.getIsDefault() == 1) {
            clearOtherDefault(platform.getId());
        }
        gitAuditService.record("update", "git_platform", platform.getId(), platform.getName(),
                toJson(buildAuditDetail(platform)));
        return toVO(platform);
    }

    @Override
    @Transactional
    public void deletePlatform(Long id) {
        GitPlatform platform = requirePlatform(id);
        gitPlatformMapper.deleteById(id);
        gitAuditService.record("delete", "git_platform", platform.getId(), platform.getName(),
                toJson(buildAuditDetail(platform)));
    }

    @Override
    public Map<String, Object> testConnection(Long id) {
        GitPlatform platform = requirePlatform(id);
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            URL url = new URL(platform.getBaseUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            result.put("connected", code >= 200 && code < 500);
            result.put("statusCode", code);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("message", code >= 200 && code < 500 ? "连接成功" : "平台返回异常状态码 " + code);
        } catch (Exception e) {
            result.put("connected", false);
            result.put("latencyMs", System.currentTimeMillis() - start);
            result.put("message", "连接失败: " + e.getMessage());
        }
        // 更新最近检测时间
        GitPlatform update = new GitPlatform();
        update.setId(id);
        update.setLastCheckedAt(LocalDateTime.now());
        gitPlatformMapper.updateById(update);
        return result;
    }

    private GitPlatform requirePlatform(Long id) {
        GitPlatform platform = gitPlatformMapper.selectById(id);
        if (platform == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Git 平台不存在: " + id);
        }
        return platform;
    }

    private void clearOtherDefault(Long excludeId) {
        LambdaUpdateWrapper<GitPlatform> wrapper = new LambdaUpdateWrapper<>();
        wrapper.ne(GitPlatform::getId, excludeId)
                .set(GitPlatform::getIsDefault, 0)
                .set(GitPlatform::getUpdatedAt, LocalDateTime.now());
        gitPlatformMapper.update(null, wrapper);
    }

    private GitPlatformVO toVO(GitPlatform platform) {
        GitPlatformVO vo = new GitPlatformVO();
        vo.setId(platform.getId());
        vo.setName(platform.getName());
        vo.setPlatformType(platform.getPlatformType());
        vo.setBaseUrl(platform.getBaseUrl());
        vo.setAuthType(platform.getAuthType());
        vo.setCredential(StringUtils.hasText(platform.getCredential()) ? CREDENTIAL_MASK : null);
        vo.setIsDefault(platform.getIsDefault());
        vo.setStatus(platform.getStatus());
        vo.setLastCheckedAt(platform.getLastCheckedAt());
        vo.setCreatedAt(platform.getCreatedAt());
        vo.setUpdatedAt(platform.getUpdatedAt());
        return vo;
    }

    private Map<String, Object> buildAuditDetail(GitPlatform platform) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", platform.getName());
        detail.put("platformType", platform.getPlatformType());
        detail.put("baseUrl", platform.getBaseUrl());
        detail.put("authType", platform.getAuthType());
        detail.put("isDefault", platform.getIsDefault());
        detail.put("status", platform.getStatus());
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
