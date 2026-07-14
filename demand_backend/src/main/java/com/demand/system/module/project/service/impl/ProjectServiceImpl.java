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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final String PROJECT_CODE_PREFIX = "PRJ-";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
    }

    /**
     * 生成项目编号: PRJ-YYYYMMDD-NNN
     * NNN 为当天已有项目数+1
     */
    private String generateProjectCode() {
        String datePart = LocalDate.now().format(DATE_FMT);
        String prefix = PROJECT_CODE_PREFIX + datePart + "-";

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(Project::getProjectCode, prefix)
               .orderByDesc(Project::getProjectCode)
               .last("LIMIT 1");
        Project last = projectMapper.selectOne(wrapper);

        int seq = 1;
        if (last != null && last.getProjectCode() != null) {
            String code = last.getProjectCode();
            String seqStr = code.substring(code.lastIndexOf('-') + 1);
            seq = Integer.parseInt(seqStr) + 1;
        }
        return prefix + String.format("%03d", seq);
    }

    @Override
    public PageResult<Project> list(String name, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Project::getName, name);
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Project::getStatus, status);
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
        // 项目编号：前端未传则自动生成，传了则使用前端值
        project.setProjectCode(dto.getProjectCode() != null && !dto.getProjectCode().isBlank()
                ? dto.getProjectCode() : generateProjectCode());
        project.setDescription(dto.getDescription());
        project.setCompanyId(dto.getCompanyId());
        project.setTeam(dto.getTeam());
        project.setLeaderId(dto.getLeaderId());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setContactPhone(dto.getContactPhone());
        project.setCreatorId(creatorId);
        project.setStatus("active");
        projectMapper.insert(project);
    }

    @Override
    public void update(ProjectUpdateDTO dto) {
        Project project = new Project();
        project.setId(dto.getId());
        project.setName(dto.getName());
        project.setProjectCode(dto.getProjectCode());
        project.setDescription(dto.getDescription());
        project.setCompanyId(dto.getCompanyId());
        project.setTeam(dto.getTeam());
        project.setLeaderId(dto.getLeaderId());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());
        project.setContactPhone(dto.getContactPhone());
        project.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "active" : dto.getStatus());
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
