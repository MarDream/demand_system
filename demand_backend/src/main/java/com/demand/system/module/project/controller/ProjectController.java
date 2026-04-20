package com.demand.system.module.project.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.project.dto.ProjectCreateDTO;
import com.demand.system.module.project.dto.ProjectMemberAddDTO;
import com.demand.system.module.project.dto.ProjectUpdateDTO;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.entity.ProjectMember;
import com.demand.system.module.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Result<PageResult<Project>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult<Project> result = projectService.list(name, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        Project project = projectService.getById(id);
        return Result.success(project);
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProjectCreateDTO dto) {
        // TODO: 后续接入 SecurityUtils 获取当前用户ID
        Long creatorId = 1L;
        projectService.create(dto, creatorId);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateDTO dto) {
        dto.setId(id);
        projectService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/members")
    public Result<List<ProjectMember>> getMembers(@PathVariable Long id) {
        List<ProjectMember> members = projectService.getMembers(id);
        return Result.success(members);
    }

    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id, @Valid @RequestBody ProjectMemberAddDTO dto) {
        projectService.addMember(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return Result.success();
    }
}
