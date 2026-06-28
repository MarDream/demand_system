package com.demand.system.module.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.result.Result;
import com.demand.system.module.workflow.dto.*;
import com.demand.system.module.workflow.service.WorkflowCopyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流复制控制器
 */
@RestController
@RequestMapping("/api/v1/workflows")
@Tag(name = "工作流复制", description = "工作流复制相关接口")
public class WorkflowCopyController {

    private final WorkflowCopyService workflowCopyService;

    public WorkflowCopyController(WorkflowCopyService workflowCopyService) {
        this.workflowCopyService = workflowCopyService;
    }

    /**
     * 复制工作流版本
     */
    @PostMapping("/versions/{versionId}/copy")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    @Operation(summary = "复制工作流", description = "从现有工作流版本创建副本")
    public Result<WorkflowCopyResponse> copyWorkflow(
            @Parameter(description = "源工作流版本ID") @PathVariable("versionId") Long versionId,
            @Valid @RequestBody WorkflowCopyRequest request,
            HttpServletRequest httpRequest) {
        
        // 获取当前用户信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long operatorId = extractUserId(auth);
        String operatorName = extractUserName(auth);
        
        // 获取请求信息
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        WorkflowCopyResponse response = workflowCopyService.copyWorkflow(
            versionId, request, operatorId, operatorName, ipAddress, userAgent
        );

        return Result.success(response);
    }

    /**
     * 获取可复制的工作流模板列表
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    @Operation(summary = "获取工作流模板列表", description = "查询可用于复制的工作流模板")
    public Result<Page<WorkflowTemplateDTO>> getTemplates(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含我的工作流") @RequestParam(defaultValue = "false") Boolean includeMyWorkflows) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = extractUserId(auth);

        Page<WorkflowTemplateDTO> pageParam = new Page<>(page, pageSize);
        Page<WorkflowTemplateDTO> result = workflowCopyService.getTemplates(
            pageParam, keyword, includeMyWorkflows, currentUserId
        );

        return Result.success(result);
    }

    /**
     * 检查工作流名称是否冲突
     */
    @GetMapping("/check-name")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    @Operation(summary = "检查名称冲突", description = "验证工作流名称是否已存在")
    public Result<Map<String, Object>> checkNameConflict(
            @Parameter(description = "工作流名称") @RequestParam String name,
            @Parameter(description = "项目ID") @RequestParam Long projectId) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = extractUserId(auth);

        boolean conflict = workflowCopyService.checkNameConflict(name, projectId, currentUserId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("conflict", conflict);
        
        if (conflict) {
            String suggestedName = workflowCopyService.generateUniqueName(name, projectId, currentUserId);
            result.put("suggestedName", suggestedName);
        }

        return Result.success(result);
    }

    /**
     * 标记工作流为模板
     */
    @PostMapping("/versions/{versionId}/mark-as-template")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN')")
    @Operation(summary = "标记为模板", description = "将工作流版本标记为可复用模板")
    public Result<Void> markAsTemplate(
            @Parameter(description = "工作流版本ID") @PathVariable("versionId") Long versionId,
            @RequestBody Map<String, Boolean> body) {
        
        Boolean isTemplate = body.getOrDefault("isTemplate", true);
        workflowCopyService.markAsTemplate(versionId, isTemplate);
        
        return Result.success();
    }

    /**
     * 获取工作流溯源树
     */
    @GetMapping("/versions/{versionId}/lineage")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:workflow:config')")
    @Operation(summary = "获取溯源树", description = "查看工作流的复制来源链")
    public Result<WorkflowLineageDTO> getLineageTree(
            @Parameter(description = "工作流版本ID") @PathVariable("versionId") Long versionId) {
        
        WorkflowLineageDTO lineage = workflowCopyService.getLineageTree(versionId);
        return Result.success(lineage);
    }

    /**
     * 从Authentication中提取用户ID
     */
    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> principal = (Map<String, Object>) auth.getPrincipal();
            Object userId = principal.get("userId");
            if (userId != null) {
                return Long.valueOf(userId.toString());
            }
        }
        return 1L; // 默认返回1（系统用户）
    }

    /**
     * 从Authentication中提取用户名
     */
    private String extractUserName(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> principal = (Map<String, Object>) auth.getPrincipal();
            Object username = principal.get("username");
            if (username != null) {
                return username.toString();
            }
        }
        return "System";
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多个IP的情况（取第一个）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
