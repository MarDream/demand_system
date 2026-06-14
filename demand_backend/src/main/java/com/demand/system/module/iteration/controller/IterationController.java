package com.demand.system.module.iteration.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.iteration.dto.IterationCreateDTO;
import com.demand.system.module.iteration.dto.IterationUpdateDTO;
import com.demand.system.module.iteration.dto.IterationVO;
import com.demand.system.module.iteration.service.IterationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IterationController {

    private final IterationService iterationService;

    public IterationController(IterationService iterationService) {
        this.iterationService = iterationService;
    }

    @GetMapping("/projects/{id}/iterations")
    @PreAuthorize("isAuthenticated()")
    public Result<List<IterationVO>> listByProject(@PathVariable Long id) {
        List<IterationVO> list = iterationService.listByProject(id);
        return Result.success(list);
    }

    @GetMapping("/iterations/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<IterationVO> getById(@PathVariable Long id) {
        IterationVO vo = iterationService.getById(id);
        return Result.success(vo);
    }

    @PostMapping("/projects/{id}/iterations")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:iteration:create')")
    public Result<Void> create(@PathVariable Long id, @RequestBody IterationCreateDTO dto) {
        dto.setProjectId(id);
        // 验证必须在setProjectId之后执行
        jakarta.validation.Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String message = violations.iterator().next().getMessage();
            return Result.fail(message);
        }
        Long userId = SecurityUtils.getCurrentUserId();
        iterationService.create(dto, userId);
        return Result.success();
    }

    @PutMapping("/iterations/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:iteration:update')")
    public Result<Void> update(@PathVariable Long id, @RequestBody IterationUpdateDTO dto) {
        dto.setId(id);
        jakarta.validation.Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String message = violations.iterator().next().getMessage();
            return Result.fail(message);
        }
        iterationService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/iterations/{id}")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:iteration:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        iterationService.delete(id);
        return Result.success();
    }

    @PostMapping("/iterations/{id}/requirements")
    @PreAuthorize("hasAnyAuthority('admin', 'SUPER_ADMIN', 'button:iteration:update')")
    public Result<Void> assignRequirements(
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> requirementIds = body.get("requirementIds");
        if (requirementIds == null || requirementIds.isEmpty()) {
            return Result.fail("需求ID列表不能为空");
        }
        iterationService.assignRequirements(id, requirementIds);
        return Result.success();
    }

    @GetMapping("/iterations/{id}/burndown")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getBurndownData(@PathVariable Long id) {
        Map<String, Object> data = iterationService.getBurndownData(id);
        return Result.success(data);
    }
}
