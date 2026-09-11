package com.demand.system.module.git.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.security.PermissionGuard;
import com.demand.system.module.git.dto.BranchProtectionRuleDTO;
import com.demand.system.module.git.dto.BranchProtectionRuleVO;
import com.demand.system.module.git.dto.ProtectionRuleSetDTO;
import com.demand.system.module.git.entity.BranchProtectionRule;
import com.demand.system.module.git.entity.GitRepository;
import com.demand.system.module.git.entity.ProtectionRuleSet;
import com.demand.system.module.git.mapper.BranchProtectionRuleMapper;
import com.demand.system.module.git.mapper.GitRepositoryMapper;
import com.demand.system.module.git.mapper.ProtectionRuleSetMapper;
import com.demand.system.module.git.service.BranchProtectionService;
import com.demand.system.module.git.service.GitAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BranchProtectionServiceImpl implements BranchProtectionService {

    private final BranchProtectionRuleMapper branchProtectionRuleMapper;
    private final ProtectionRuleSetMapper protectionRuleSetMapper;
    private final GitRepositoryMapper gitRepositoryMapper;
    private final GitAuditService gitAuditService;
    private final ObjectMapper objectMapper;

    public BranchProtectionServiceImpl(BranchProtectionRuleMapper branchProtectionRuleMapper,
                                       ProtectionRuleSetMapper protectionRuleSetMapper,
                                       GitRepositoryMapper gitRepositoryMapper,
                                       GitAuditService gitAuditService,
                                       ObjectMapper objectMapper) {
        this.branchProtectionRuleMapper = branchProtectionRuleMapper;
        this.protectionRuleSetMapper = protectionRuleSetMapper;
        this.gitRepositoryMapper = gitRepositoryMapper;
        this.gitAuditService = gitAuditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<BranchProtectionRuleVO> listRules(Long repoId) {
        return branchProtectionRuleMapper.selectList(new LambdaQueryWrapper<BranchProtectionRule>()
                        .eq(BranchProtectionRule::getRepoId, repoId)
                        .orderByAsc(BranchProtectionRule::getPriority)
                        .orderByDesc(BranchProtectionRule::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public BranchProtectionRuleVO createRule(Long repoId, BranchProtectionRuleDTO dto) {
        requireRepository(repoId);
        BranchProtectionRule rule = new BranchProtectionRule();
        rule.setRepoId(repoId);
        applyDto(rule, dto);
        rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : 1);
        rule.setCreatedBy(PermissionGuard.requireCurrentUserId());
        branchProtectionRuleMapper.insert(rule);

        gitAuditService.record("create", "protection_rule", rule.getId(), rule.getRuleName(),
                toJson(buildAuditDetail(rule)));
        return toVO(rule);
    }

    @Override
    @Transactional
    public BranchProtectionRuleVO updateRule(Long id, BranchProtectionRuleDTO dto) {
        BranchProtectionRule rule = requireRule(id);
        applyDto(rule, dto);
        if (dto.getEnabled() != null) {
            rule.setEnabled(dto.getEnabled());
        }
        rule.setUpdatedAt(LocalDateTime.now());
        branchProtectionRuleMapper.updateById(rule);

        gitAuditService.record("update", "protection_rule", rule.getId(), rule.getRuleName(),
                toJson(buildAuditDetail(rule)));
        return toVO(rule);
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        BranchProtectionRule rule = requireRule(id);
        branchProtectionRuleMapper.deleteById(id);
        gitAuditService.record("delete", "protection_rule", rule.getId(), rule.getRuleName(),
                toJson(buildAuditDetail(rule)));
    }

    @Override
    @Transactional
    public void toggleRule(Long id, Integer enabled) {
        BranchProtectionRule rule = requireRule(id);
        Integer target = enabled != null && enabled == 1 ? 1 : 0;
        rule.setEnabled(target);
        rule.setUpdatedAt(LocalDateTime.now());
        branchProtectionRuleMapper.updateById(rule);
        gitAuditService.record("toggle", "protection_rule", rule.getId(), rule.getRuleName(),
                toJson(buildAuditDetail(rule)));
    }

    @Override
    public List<ProtectionRuleSetDTO> listRuleSets() {
        return protectionRuleSetMapper.selectList(new LambdaQueryWrapper<ProtectionRuleSet>()
                        .orderByDesc(ProtectionRuleSet::getId))
                .stream().map(this::toRuleSetDTO).toList();
    }

    @Override
    @Transactional
    public ProtectionRuleSetDTO createRuleSet(ProtectionRuleSetDTO dto) {
        ProtectionRuleSet ruleSet = new ProtectionRuleSet();
        ruleSet.setName(dto.getName().trim());
        ruleSet.setDescription(dto.getDescription());
        ruleSet.setCreatedBy(PermissionGuard.requireCurrentUserId());
        protectionRuleSetMapper.insert(ruleSet);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", ruleSet.getName());
        detail.put("description", ruleSet.getDescription());
        gitAuditService.record("create", "protection_rule_set", ruleSet.getId(), ruleSet.getName(), toJson(detail));
        return toRuleSetDTO(ruleSet);
    }

    @Override
    @Transactional
    public BranchProtectionRuleVO applyRuleSet(Long ruleSetId, Long repoId) {
        if (repoId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "repoId 不能为空");
        }
        requireRepository(repoId);
        ProtectionRuleSet ruleSet = protectionRuleSetMapper.selectById(ruleSetId);
        if (ruleSet == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "保护规则集不存在: " + ruleSetId);
        }
        // 简化实现：将规则集以"禁止直接推送 + 必须 MR 审批"的默认模板落到该仓库
        BranchProtectionRule rule = new BranchProtectionRule();
        rule.setRepoId(repoId);
        rule.setRuleSetId(ruleSetId);
        rule.setRuleName(ruleSet.getName());
        rule.setBranchPattern("*");
        rule.setPriority(0);
        rule.setForbidPush(1);
        rule.setRequireMr(1);
        rule.setMinApprovals(1);
        rule.setEnabled(1);
        rule.setCreatedBy(PermissionGuard.requireCurrentUserId());
        branchProtectionRuleMapper.insert(rule);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("ruleSetId", ruleSetId);
        detail.put("repoId", repoId);
        gitAuditService.record("apply", "protection_rule", rule.getId(), rule.getRuleName(), toJson(detail));
        return toVO(rule);
    }

    private void applyDto(BranchProtectionRule rule, BranchProtectionRuleDTO dto) {
        if (StringUtils.hasText(dto.getRuleName())) {
            rule.setRuleName(dto.getRuleName().trim());
        }
        if (dto.getBranchPattern() != null) {
            rule.setBranchPattern(dto.getBranchPattern());
        }
        if (dto.getPriority() != null) {
            rule.setPriority(dto.getPriority());
        }
        if (dto.getForbidPush() != null) {
            rule.setForbidPush(dto.getForbidPush());
        }
        if (dto.getForbidForcePush() != null) {
            rule.setForbidForcePush(dto.getForbidForcePush());
        }
        if (dto.getForbidDelete() != null) {
            rule.setForbidDelete(dto.getForbidDelete());
        }
        if (dto.getRequireMr() != null) {
            rule.setRequireMr(dto.getRequireMr());
        }
        if (dto.getMinApprovals() != null) {
            rule.setMinApprovals(dto.getMinApprovals());
        }
        if (dto.getDismissStaleApprovals() != null) {
            rule.setDismissStaleApprovals(dto.getDismissStaleApprovals());
        }
        if (dto.getBlockSelfApprove() != null) {
            rule.setBlockSelfApprove(dto.getBlockSelfApprove());
        }
        if (dto.getRequireCodeownerApproval() != null) {
            rule.setRequireCodeownerApproval(dto.getRequireCodeownerApproval());
        }
        if (dto.getRequireThreadResolved() != null) {
            rule.setRequireThreadResolved(dto.getRequireThreadResolved());
        }
        if (dto.getRequireCiPass() != null) {
            rule.setRequireCiPass(dto.getRequireCiPass());
        }
        if (dto.getRequireUpToDate() != null) {
            rule.setRequireUpToDate(dto.getRequireUpToDate());
        }
        if (dto.getCiContexts() != null) {
            rule.setCiContexts(dto.getCiContexts());
        }
        if (dto.getWhitelistUsers() != null) {
            rule.setWhitelistUsers(toJsonList(dto.getWhitelistUsers()));
        }
        if (dto.getWhitelistRoles() != null) {
            rule.setWhitelistRoles(toJsonList(dto.getWhitelistRoles()));
        }
    }

    private BranchProtectionRule requireRule(Long id) {
        BranchProtectionRule rule = branchProtectionRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分支保护规则不存在: " + id);
        }
        return rule;
    }

    private GitRepository requireRepository(Long repoId) {
        GitRepository repo = gitRepositoryMapper.selectById(repoId);
        if (repo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在: " + repoId);
        }
        return repo;
    }

    private BranchProtectionRuleVO toVO(BranchProtectionRule rule) {
        BranchProtectionRuleVO vo = new BranchProtectionRuleVO();
        vo.setId(rule.getId());
        vo.setRepoId(rule.getRepoId());
        vo.setRuleSetId(rule.getRuleSetId());
        vo.setRuleName(rule.getRuleName());
        vo.setBranchPattern(rule.getBranchPattern());
        vo.setPriority(rule.getPriority());
        vo.setForbidPush(rule.getForbidPush());
        vo.setForbidForcePush(rule.getForbidForcePush());
        vo.setForbidDelete(rule.getForbidDelete());
        vo.setRequireMr(rule.getRequireMr());
        vo.setMinApprovals(rule.getMinApprovals());
        vo.setDismissStaleApprovals(rule.getDismissStaleApprovals());
        vo.setBlockSelfApprove(rule.getBlockSelfApprove());
        vo.setRequireCodeownerApproval(rule.getRequireCodeownerApproval());
        vo.setRequireThreadResolved(rule.getRequireThreadResolved());
        vo.setRequireCiPass(rule.getRequireCiPass());
        vo.setRequireUpToDate(rule.getRequireUpToDate());
        vo.setCiContexts(rule.getCiContexts());
        vo.setWhitelistUsers(parseLongList(rule.getWhitelistUsers()));
        vo.setWhitelistRoles(parseStringList(rule.getWhitelistRoles()));
        vo.setEnabled(rule.getEnabled());
        vo.setCreatedAt(rule.getCreatedAt());
        vo.setUpdatedAt(rule.getUpdatedAt());
        return vo;
    }

    private ProtectionRuleSetDTO toRuleSetDTO(ProtectionRuleSet ruleSet) {
        ProtectionRuleSetDTO dto = new ProtectionRuleSetDTO();
        dto.setId(ruleSet.getId());
        dto.setName(ruleSet.getName());
        dto.setDescription(ruleSet.getDescription());
        dto.setCreatedAt(ruleSet.getCreatedAt());
        return dto;
    }

    private Map<String, Object> buildAuditDetail(BranchProtectionRule rule) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("repoId", rule.getRepoId());
        detail.put("ruleName", rule.getRuleName());
        detail.put("branchPattern", rule.getBranchPattern());
        detail.put("enabled", rule.getEnabled());
        return detail;
    }

    private String toJsonList(List<?> list) {
        try {
            return list == null ? "[]" : objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }
}
