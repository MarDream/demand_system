package com.demand.system.module.git.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.security.PermissionGuard;
import com.demand.system.module.git.dto.MergeRequestDTO;
import com.demand.system.module.git.dto.MergeRequestVO;
import com.demand.system.module.git.entity.BranchProtectionRule;
import com.demand.system.module.git.entity.GitRepository;
import com.demand.system.module.git.entity.MergeRequest;
import com.demand.system.module.git.mapper.BranchProtectionRuleMapper;
import com.demand.system.module.git.mapper.GitRepositoryMapper;
import com.demand.system.module.git.mapper.MergeRequestMapper;
import com.demand.system.module.git.service.GitAuditService;
import com.demand.system.module.git.service.MergeRequestService;
import com.demand.system.module.user.entity.User;
import com.demand.system.module.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MergeRequestServiceImpl implements MergeRequestService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_MERGED = "MERGED";

    /**
     * 简化方案：审批记录保存在内存 Map 中（MR id -> 审批人 id 集合），
     * 同时每次审批会写一条 git_audit_logs 审计记录。
     */
    private final Map<Long, Set<Long>> approverRecords = new ConcurrentHashMap<>();

    private final MergeRequestMapper mergeRequestMapper;
    private final GitRepositoryMapper gitRepositoryMapper;
    private final BranchProtectionRuleMapper branchProtectionRuleMapper;
    private final UserMapper userMapper;
    private final GitAuditService gitAuditService;
    private final ObjectMapper objectMapper;

    public MergeRequestServiceImpl(MergeRequestMapper mergeRequestMapper,
                                   GitRepositoryMapper gitRepositoryMapper,
                                   BranchProtectionRuleMapper branchProtectionRuleMapper,
                                   UserMapper userMapper,
                                   GitAuditService gitAuditService,
                                   ObjectMapper objectMapper) {
        this.mergeRequestMapper = mergeRequestMapper;
        this.gitRepositoryMapper = gitRepositoryMapper;
        this.branchProtectionRuleMapper = branchProtectionRuleMapper;
        this.userMapper = userMapper;
        this.gitAuditService = gitAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MergeRequestVO> listMergeRequests(Long repoId) {
        return mergeRequestMapper.selectList(new LambdaQueryWrapper<MergeRequest>()
                        .eq(MergeRequest::getRepoId, repoId)
                        .orderByDesc(MergeRequest::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public MergeRequestVO createMergeRequest(Long repoId, MergeRequestDTO dto) {
        requireRepository(repoId);
        MergeRequest mr = new MergeRequest();
        mr.setRepoId(repoId);
        mr.setSourceBranch(dto.getSourceBranch());
        mr.setTargetBranch(dto.getTargetBranch());
        mr.setTitle(dto.getTitle().trim());
        mr.setDescription(dto.getDescription());
        mr.setAuthorId(PermissionGuard.requireCurrentUserId());
        mr.setStatus(STATUS_OPEN);
        mr.setMergeStrategy(StringUtils.hasText(dto.getMergeStrategy()) ? dto.getMergeStrategy() : "merge_commit");
        mr.setCiStatus(StringUtils.hasText(dto.getCiStatus()) ? dto.getCiStatus() : "pending");
        mr.setRequirementId(dto.getRequirementId());
        mr.setRemoteMrId(dto.getRemoteMrId());
        mergeRequestMapper.insert(mr);

        gitAuditService.record("create", "merge_request", mr.getId(), mr.getTitle(),
                toJson(buildAuditDetail(mr)));
        return toVO(mr);
    }

    @Override
    public MergeRequestVO getMergeRequestDetail(Long id) {
        return toVO(requireMergeRequest(id));
    }

    @Override
    @Transactional
    public MergeRequestVO approveMergeRequest(Long id) {
        MergeRequest mr = requireMergeRequest(id);
        if (!STATUS_OPEN.equals(mr.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅 OPEN 状态的合并请求可审批");
        }
        Long currentUserId = PermissionGuard.requireCurrentUserId();
        if (isBlockSelfApprove(mr.getRepoId()) && Objects.equals(mr.getAuthorId(), currentUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该仓库禁止审批自己创建的合并请求");
        }
        mr.setStatus(STATUS_APPROVED);
        mergeRequestMapper.updateById(mr);
        approverRecords.computeIfAbsent(mr.getId(), k -> new LinkedHashSet<>()).add(currentUserId);

        gitAuditService.record("approve", "merge_request", mr.getId(), mr.getTitle(),
                toJson(buildAuditDetail(mr)));
        return toVO(mr);
    }

    @Override
    @Transactional
    public MergeRequestVO requestChanges(Long id) {
        MergeRequest mr = requireMergeRequest(id);
        if (STATUS_MERGED.equals(mr.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已合并的请求无法回退");
        }
        mr.setStatus(STATUS_OPEN);
        mergeRequestMapper.updateById(mr);
        approverRecords.remove(mr.getId());

        gitAuditService.record("request_changes", "merge_request", mr.getId(), mr.getTitle(),
                toJson(buildAuditDetail(mr)));
        return toVO(mr);
    }

    @Override
    @Transactional
    public MergeRequestVO mergeMergeRequest(Long id) {
        MergeRequest mr = requireMergeRequest(id);
        if (STATUS_MERGED.equals(mr.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该合并请求已合并");
        }
        // 该仓库要求 MR 审批且 min_approvals > 0 时，必须已审批通过
        if (requiresApproval(mr.getRepoId()) && !STATUS_APPROVED.equals(mr.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该仓库要求审批通过后才能合并");
        }
        mr.setStatus(STATUS_MERGED);
        mr.setMergedBy(PermissionGuard.requireCurrentUserId());
        mr.setMergedAt(LocalDateTime.now());
        mergeRequestMapper.updateById(mr);

        gitAuditService.record("merge", "merge_request", mr.getId(), mr.getTitle(),
                toJson(buildAuditDetail(mr)));
        return toVO(mr);
    }

    private boolean isBlockSelfApprove(Long repoId) {
        return findRules(repoId).stream().anyMatch(rule -> isOne(rule.getRequireMr())
                && isOne(rule.getBlockSelfApprove()));
    }

    private boolean requiresApproval(Long repoId) {
        return findRules(repoId).stream().anyMatch(rule -> isOne(rule.getRequireMr())
                && rule.getMinApprovals() != null && rule.getMinApprovals() > 0);
    }

    private List<BranchProtectionRule> findRules(Long repoId) {
        return branchProtectionRuleMapper.selectList(new LambdaQueryWrapper<BranchProtectionRule>()
                .eq(BranchProtectionRule::getRepoId, repoId)
                .eq(BranchProtectionRule::getEnabled, 1));
    }

    private boolean isOne(Integer value) {
        return value != null && value == 1;
    }

    private MergeRequest requireMergeRequest(Long id) {
        MergeRequest mr = mergeRequestMapper.selectById(id);
        if (mr == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "合并请求不存在: " + id);
        }
        return mr;
    }

    private GitRepository requireRepository(Long repoId) {
        GitRepository repo = gitRepositoryMapper.selectById(repoId);
        if (repo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在: " + repoId);
        }
        return repo;
    }

    private MergeRequestVO toVO(MergeRequest mr) {
        MergeRequestVO vo = new MergeRequestVO();
        vo.setId(mr.getId());
        vo.setRepoId(mr.getRepoId());
        vo.setRepoName(resolveRepoName(mr.getRepoId()));
        vo.setSourceBranch(mr.getSourceBranch());
        vo.setTargetBranch(mr.getTargetBranch());
        vo.setTitle(mr.getTitle());
        vo.setDescription(mr.getDescription());
        vo.setAuthorId(mr.getAuthorId());
        vo.setAuthorName(resolveUserName(mr.getAuthorId()));
        vo.setStatus(mr.getStatus());
        vo.setMergeStrategy(mr.getMergeStrategy());
        vo.setCiStatus(mr.getCiStatus());
        vo.setRequirementId(mr.getRequirementId());
        vo.setRemoteMrId(mr.getRemoteMrId());
        vo.setMergedBy(mr.getMergedBy());
        vo.setMergedAt(mr.getMergedAt());
        vo.setCreatedAt(mr.getCreatedAt());
        vo.setUpdatedAt(mr.getUpdatedAt());
        return vo;
    }

    private String resolveRepoName(Long repoId) {
        if (repoId == null) {
            return null;
        }
        GitRepository repo = gitRepositoryMapper.selectById(repoId);
        return repo != null ? repo.getName() : null;
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

    private Map<String, Object> buildAuditDetail(MergeRequest mr) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("repoId", mr.getRepoId());
        detail.put("sourceBranch", mr.getSourceBranch());
        detail.put("targetBranch", mr.getTargetBranch());
        detail.put("title", mr.getTitle());
        detail.put("status", mr.getStatus());
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
