package com.demand.system.module.git.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.security.PermissionGuard;
import com.demand.system.module.git.dto.GitAuditLogVO;
import com.demand.system.module.git.entity.GitAuditLog;
import com.demand.system.module.git.mapper.GitAuditLogMapper;
import com.demand.system.module.git.service.GitAuditService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GitAuditServiceImpl implements GitAuditService {

    private final GitAuditLogMapper gitAuditLogMapper;
    private final UserMapper userMapper;

    public GitAuditServiceImpl(GitAuditLogMapper gitAuditLogMapper, UserMapper userMapper) {
        this.gitAuditLogMapper = gitAuditLogMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public void record(String action, String targetType, Long targetId, String targetName, String detail) {
        GitAuditLog log = new GitAuditLog();
        log.setOperatorId(PermissionGuard.requireCurrentUserId());
        log.setOperatorIp(extractClientIp());
        log.setUserAgent(extractUserAgent());
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setAction(action);
        log.setDetail(detail);
        gitAuditLogMapper.insert(log);
    }

    @Override
    public List<GitAuditLogVO> listAuditLogs(String keyword, Long operatorId, String targetType, String action,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<GitAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(GitAuditLog::getTargetName, keyword)
                    .or().like(GitAuditLog::getDetail, keyword));
        }
        if (operatorId != null) {
            wrapper.eq(GitAuditLog::getOperatorId, operatorId);
        }
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(GitAuditLog::getTargetType, targetType);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(GitAuditLog::getAction, action);
        }
        if (startTime != null) {
            wrapper.ge(GitAuditLog::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(GitAuditLog::getCreatedAt, endTime);
        }
        wrapper.orderByDesc(GitAuditLog::getId);

        return gitAuditLogMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    private GitAuditLogVO toVO(GitAuditLog log) {
        GitAuditLogVO vo = new GitAuditLogVO();
        vo.setId(log.getId());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorName(resolveUserName(log.getOperatorId()));
        vo.setOperatorIp(log.getOperatorIp());
        vo.setUserAgent(log.getUserAgent());
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId());
        vo.setTargetName(log.getTargetName());
        vo.setAction(log.getAction());
        vo.setDetail(log.getDetail());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
    }

    private String extractClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
