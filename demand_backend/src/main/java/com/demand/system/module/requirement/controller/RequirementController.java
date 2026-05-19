package com.demand.system.module.requirement.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.dto.RequirementDraftCreateDTO;
import com.demand.system.module.requirement.dto.RequirementDraftUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementMyListQueryDTO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementSubmitDTO;
import com.demand.system.module.requirement.dto.NextNodeOptionDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.service.RequirementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/requirements")
public class RequirementController {

    private final RequirementService requirementService;

    public RequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @GetMapping
    public Result<PageResult<RequirementVO>> list(RequirementQueryDTO query) {
        return Result.success(requirementService.list(query));
    }

    @GetMapping("/{id}")
    public Result<RequirementVO> getDetail(@PathVariable Long id) {
        return Result.success(requirementService.getDetail(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Void> create(@Valid @RequestBody RequirementCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.create(dto, userId);
        return Result.success();
    }

    @PostMapping("/drafts")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createDraft(@RequestBody RequirementDraftCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.createDraft(dto, userId));
    }

    @PutMapping("/{id}/draft")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateDraft(@PathVariable Long id, @RequestBody RequirementDraftUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        dto.setId(id);
        requirementService.updateDraft(dto, userId);
        return Result.success();
    }

    @GetMapping("/my-drafts")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<RequirementVO>> listMyDrafts(RequirementMyListQueryDTO query) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.listMyDrafts(query, userId));
    }

    @GetMapping("/my-pending")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<RequirementVO>> listMyPending(RequirementMyListQueryDTO query) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.listMyPending(query, userId));
    }

    @GetMapping("/{id}/next-nodes")
    @PreAuthorize("isAuthenticated()")
    public Result<List<NextNodeOptionDTO>> getNextNodes(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.getNextNodes(id, userId));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public Result<RequirementVO> submit(@PathVariable Long id, @RequestBody RequirementSubmitDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        return Result.success(requirementService.submit(id, dto, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RequirementUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        dto.setId(id);
        requirementService.update(dto, userId);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.delete(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> restore(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.restore(id, userId);
        return Result.success();
    }

    @GetMapping("/{id}/history")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable Long id) {
        return Result.success(requirementService.getHistory(id));
    }

    @GetMapping("/{id}/comments")
    public Result<List<RequirementCommentVO>> getComments(@PathVariable Long id) {
        return Result.success(requirementService.getComments(id));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> addComment(@PathVariable Long id, @Valid @RequestBody RequirementCommentCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录或登录已过期");
        }
        requirementService.addComment(id, dto, userId);
        return Result.success();
    }

    @GetMapping("/{id}/children")
    public Result<List<Map<String, Object>>> getChildren(@PathVariable Long id) {
        return Result.success(requirementService.getChildren(id));
    }
}
