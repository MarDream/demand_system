package com.demand.system.module.project.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.project.dto.ProjectCreateDTO;
import com.demand.system.module.project.dto.ProjectMemberAddDTO;
import com.demand.system.module.project.dto.ProjectUpdateDTO;
import com.demand.system.module.project.entity.Project;
import com.demand.system.module.project.entity.ProjectMember;

import java.util.List;

public interface ProjectService {

    PageResult<Project> list(String name, int pageNum, int pageSize);

    Project getById(Long id);

    void create(ProjectCreateDTO dto, Long creatorId);

    void update(ProjectUpdateDTO dto);

    void delete(Long id);

    List<ProjectMember> getMembers(Long projectId);

    void addMember(Long projectId, ProjectMemberAddDTO dto);

    void removeMember(Long projectId, Long userId);
}
