package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBaseCustomRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BitableBaseCustomRoleMapper extends BaseMapper<BitableBaseCustomRole> {

    @Select("SELECT * FROM bitable_base_custom_roles WHERE base_id = #{baseId} AND deleted_at = 0 ORDER BY sort_order ASC, id ASC")
    List<BitableBaseCustomRole> selectByBaseId(@Param("baseId") Long baseId);

    /**
     * 全局自定义角色列表（跨 Base）：角色创建后全局有效，高级权限面板在任何 Base 下都要能看到全部自定义角色。
     */
    @Select("SELECT * FROM bitable_base_custom_roles WHERE deleted_at = 0 ORDER BY sort_order ASC, id ASC")
    List<BitableBaseCustomRole> selectGlobalAll();

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM bitable_base_custom_roles WHERE base_id = #{baseId} AND deleted_at = 0")
    Integer selectMaxSortOrder(@Param("baseId") Long baseId);

    /**
     * 查询用户在指定 Base 中已加入的自定义角色 ID。
     * <p>
     * 字段权限需要叠加用户「全部角色」的配置，因此这里按成员关系反查。
     */
    @Select("SELECT r.id FROM bitable_base_custom_roles r "
            + "INNER JOIN bitable_base_custom_role_members m ON m.role_id = r.id "
            + "WHERE r.base_id = #{baseId} AND r.deleted_at = 0 "
            + "AND m.member_type = 'user' AND m.member_id = #{userId}")
    List<Long> selectCustomRoleIdsByMember(@Param("baseId") Long baseId, @Param("userId") Long userId);

    /**
     * 全局自定义角色成员查询：用户在任意 Base 加入的自定义角色都返回（角色全局生效）。
     */
    @Select("SELECT m.role_id FROM bitable_base_custom_role_members m "
            + "INNER JOIN bitable_base_custom_roles r ON m.role_id = r.id "
            + "WHERE r.deleted_at = 0 "
            + "AND m.member_type = 'user' AND m.member_id = #{userId}")
    List<Long> selectGlobalCustomRoleIdsByMember(@Param("userId") Long userId);
}
