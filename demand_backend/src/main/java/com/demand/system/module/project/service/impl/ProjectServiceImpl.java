package com.demand.system.module.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.project.dto.ProjectCreateDTO;
import com.demand.system.module.project.dto.ProjectMemberAddDTO;
import com.demand.system.module.project.dto.ProjectUpdateDTO;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.entity.ProjectMember;
import com.demand.system.module.project.mapper.ProjectMapper;
import com.demand.system.module.project.mapper.ProjectMemberMapper;
import com.demand.system.module.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public PageResult<Project> list(String name, int pageNum, int pageSize) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Project::getName, name);
        }
        wrapper.orderByDesc(Project::getCreatedAt);

        Page<Project> page = new Page<>(pageNum, pageSize);
        Page<Project> result = projectMapper.selectPage(page, wrapper);

        return new PageResult<>(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    @Override
    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public void create(ProjectCreateDTO dto, Long creatorId) {
        Project project = new Project();
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setCreatorId(creatorId);
        project.setStatus("active");
        projectMapper.insert(project);
    }

    @Override
    public void update(ProjectUpdateDTO dto) {
        Project project = new Project();
        project.setId(dto.getId());
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setStatus(dto.getStatus());
        projectMapper.updateById(project);
    }

    @Override
    public void delete(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public List<ProjectMember> getMembers(Long projectId) {
        return projectMemberMapper.selectMembersWithUser(projectId);
    }

    @Override
    public void addMember(Long projectId, ProjectMemberAddDTO dto) {
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId)
               .eq(ProjectMember::getUserId, dto.getUserId());
        long count = projectMemberMapper.selectCount(wrapper);
        if (count > 0) {
            throw new IllegalArgumentException("该用户已是项目成员");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(dto.getUserId());
        member.setRole(dto.getRole());
        projectMemberMapper.insert(member);
    }

    @Override
    public void removeMember(Long projectId, Long userId) {
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId)
               .eq(ProjectMember::getUserId, userId);
        projectMemberMapper.delete(wrapper);
    }
}
