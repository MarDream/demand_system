package com.demand.system.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.project.entity.ProjectMember;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /**
     * 查询项目成员列表（含用户信息）
     *
     * @param projectId 项目ID
     * @return 项目成员列表
     */
    List<ProjectMember> selectMembersWithUser(@Param("projectId") Long projectId);
}
