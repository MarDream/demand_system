package com.demand.system.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.project.entity.ProjectMember;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    @Select("SELECT pm.*, u.username, u.real_name FROM project_members pm LEFT JOIN users u ON pm.user_id = u.id WHERE pm.project_id = #{projectId}")
    List<ProjectMember> selectMembersWithUser(@Param("projectId") Long projectId);
}
