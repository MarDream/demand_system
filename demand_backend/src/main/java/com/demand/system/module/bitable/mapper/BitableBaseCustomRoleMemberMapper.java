package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBaseCustomRoleMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BitableBaseCustomRoleMemberMapper extends BaseMapper<BitableBaseCustomRoleMember> {

    @Select("SELECT * FROM bitable_base_custom_role_members WHERE role_id = #{roleId}")
    List<BitableBaseCustomRoleMember> selectByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT * FROM bitable_base_custom_role_members WHERE role_id IN (${roleIds})")
    List<BitableBaseCustomRoleMember> selectByRoleIds(@Param("roleIds") String roleIds);
}
