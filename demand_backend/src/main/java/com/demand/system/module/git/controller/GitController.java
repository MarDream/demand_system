package com.demand.system.module.git.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.git.dto.BranchProtectionRuleDTO;
import com.demand.system.module.git.dto.BranchProtectionRuleVO;
import com.demand.system.module.git.dto.GitAuditLogVO;
import com.demand.system.module.git.dto.GitPlatformDTO;
import com.demand.system.module.git.dto.GitPlatformVO;
import com.demand.system.module.git.dto.GitRepositoryDTO;
import com.demand.system.module.git.dto.GitRepositoryVO;
import com.demand.system.module.git.dto.MergeRequestDTO;
import com.demand.system.module.git.dto.MergeRequestVO;
import com.demand.system.module.git.dto.ProtectionRuleSetDTO;
import com.demand.system.module.git.service.BranchProtectionService;
import com.demand.system.module.git.service.GitAuditService;
import com.demand.system.module.git.service.GitPlatformService;
import com.demand.system.module.git.service.GitRepositoryService;
import com.demand.system.module.git.service.MergeRequestService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/git")
@PreAuthorize("isAuthenticated()")
public class GitController {

    private final GitPlatformService gitPlatformService;
    private final GitRepositoryService gitRepositoryService;
    private final BranchProtectionService branchProtectionService;
    private final MergeRequestService mergeRequestService;
    private final GitAuditService gitAuditService;

    public GitController(GitPlatformService gitPlatformService,
                         GitRepositoryService gitRepositoryService,
                         BranchProtectionService branchProtectionService,
                         MergeRequestService mergeRequestService,
                         GitAuditService gitAuditService) {
        this.gitPlatformService = gitPlatformService;
        this.gitRepositoryService = gitRepositoryService;
        this.branchProtectionService = branchProtectionService;
        this.mergeRequestService = mergeRequestService;
        this.gitAuditService = gitAuditService;
    }

    // ==================== Git 平台 ====================

    @GetMapping("/platforms")
    public Result<List<GitPlatformVO>> listPlatforms() {
        return Result.success(gitPlatformService.listPlatforms());
    }

    @PostMapping("/platforms")
    @PreAuthorize("hasAuthority('admin')")
    public Result<GitPlatformVO> createPlatform(@Valid @RequestBody GitPlatformDTO dto) {
        return Result.success(gitPlatformService.createPlatform(dto));
    }

    @PutMapping("/platforms/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<GitPlatformVO> updatePlatform(@PathVariable Long id, @RequestBody GitPlatformDTO dto) {
        return Result.success(gitPlatformService.updatePlatform(id, dto));
    }

    @DeleteMapping("/platforms/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> deletePlatform(@PathVariable Long id) {
        gitPlatformService.deletePlatform(id);
        return Result.success();
    }

    @PostMapping("/platforms/{id}/test")
    public Result<Map<String, Object>> testPlatform(@PathVariable Long id) {
        return Result.success(gitPlatformService.testConnection(id));
    }

    // ==================== 仓库 ====================

    @GetMapping("/repositories")
    public Result<List<GitRepositoryVO>> listRepositories(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long platformId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        return Result.success(gitRepositoryService.listRepositories(keyword, platformId, projectId, status));
    }

    @PostMapping("/repositories")
    @PreAuthorize("hasAuthority('admin')")
    public Result<GitRepositoryVO> createRepository(@Valid @RequestBody GitRepositoryDTO dto) {
        return Result.success(gitRepositoryService.createRepository(dto));
    }

    @GetMapping("/repositories/{id}")
    public Result<GitRepositoryVO> getRepositoryDetail(@PathVariable Long id) {
        return Result.success(gitRepositoryService.getRepositoryDetail(id));
    }

    @PutMapping("/repositories/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<GitRepositoryVO> updateRepository(@PathVariable Long id, @RequestBody GitRepositoryDTO dto) {
        return Result.success(gitRepositoryService.updateRepository(id, dto));
    }

    @PostMapping("/repositories/{id}/archive")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> archiveRepository(@PathVariable Long id) {
        gitRepositoryService.archiveRepository(id);
        return Result.success();
    }

    @DeleteMapping("/repositories/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> deleteRepository(@PathVariable Long id) {
        gitRepositoryService.deleteRepository(id);
        return Result.success();
    }

    // ==================== 分支保护规则 ====================

    @GetMapping("/repositories/{id}/protection/rules")
    public Result<List<BranchProtectionRuleVO>> listProtectionRules(@PathVariable Long id) {
        return Result.success(branchProtectionService.listRules(id));
    }

    @PostMapping("/repositories/{id}/protection/rules")
    @PreAuthorize("hasAuthority('admin')")
    public Result<BranchProtectionRuleVO> createProtectionRule(@PathVariable Long id,
                                                               @Valid @RequestBody BranchProtectionRuleDTO dto) {
        return Result.success(branchProtectionService.createRule(id, dto));
    }

    @PutMapping("/protection/rules/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<BranchProtectionRuleVO> updateProtectionRule(@PathVariable Long id,
                                                               @RequestBody BranchProtectionRuleDTO dto) {
        return Result.success(branchProtectionService.updateRule(id, dto));
    }

    @DeleteMapping("/protection/rules/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> deleteProtectionRule(@PathVariable Long id) {
        branchProtectionService.deleteRule(id);
        return Result.success();
    }

    @PutMapping("/protection/rules/{id}/toggle")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Void> toggleProtectionRule(@PathVariable Long id, @RequestParam Integer enabled) {
        branchProtectionService.toggleRule(id, enabled);
        return Result.success();
    }

    @GetMapping("/protection/rule-sets")
    public Result<List<ProtectionRuleSetDTO>> listRuleSets() {
        return Result.success(branchProtectionService.listRuleSets());
    }

    @PostMapping("/protection/rule-sets")
    @PreAuthorize("hasAuthority('admin')")
    public Result<ProtectionRuleSetDTO> createRuleSet(@Valid @RequestBody ProtectionRuleSetDTO dto) {
        return Result.success(branchProtectionService.createRuleSet(dto));
    }

    @PostMapping("/protection/rule-sets/{id}/apply")
    @PreAuthorize("hasAuthority('admin')")
    public Result<BranchProtectionRuleVO> applyRuleSet(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long repoId = body.get("repoId") != null ? Long.valueOf(body.get("repoId").toString()) : null;
        return Result.success(branchProtectionService.applyRuleSet(id, repoId));
    }

    // ==================== 合并请求 ====================

    @GetMapping("/repositories/{id}/merge-requests")
    public Result<List<MergeRequestVO>> listMergeRequests(@PathVariable Long id) {
        return Result.success(mergeRequestService.listMergeRequests(id));
    }

    @PostMapping("/repositories/{id}/merge-requests")
    public Result<MergeRequestVO> createMergeRequest(@PathVariable Long id, @Valid @RequestBody MergeRequestDTO dto) {
        return Result.success(mergeRequestService.createMergeRequest(id, dto));
    }

    @GetMapping("/merge-requests/{id}")
    public Result<MergeRequestVO> getMergeRequestDetail(@PathVariable Long id) {
        return Result.success(mergeRequestService.getMergeRequestDetail(id));
    }

    @PostMapping("/merge-requests/{id}/approve")
    public Result<MergeRequestVO> approveMergeRequest(@PathVariable Long id) {
        return Result.success(mergeRequestService.approveMergeRequest(id));
    }

    @PostMapping("/merge-requests/{id}/request-changes")
    public Result<MergeRequestVO> requestChanges(@PathVariable Long id) {
        return Result.success(mergeRequestService.requestChanges(id));
    }

    @PostMapping("/merge-requests/{id}/merge")
    public Result<MergeRequestVO> mergeMergeRequest(@PathVariable Long id) {
        return Result.success(mergeRequestService.mergeMergeRequest(id));
    }

    // ==================== 审计日志 ====================

    @GetMapping("/audit-logs")
    public Result<List<GitAuditLogVO>> listAuditLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(gitAuditService.listAuditLogs(keyword, operatorId, targetType, action, startTime, endTime));
    }
}
