package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBaseRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BitableBaseRolePermissionMapper extends BaseMapper<BitableBaseRolePermission> {

    @Select("SELECT * FROM bitable_base_role_permissions WHERE base_id = #{baseId} AND table_id = #{tableId} AND permission_type = #{permissionType}")
    List<BitableBaseRolePermission> selectByBaseTableAndType(
            @Param("baseId") Long baseId,
            @Param("tableId") Long tableId,
            @Param("permissionType") String permissionType);

    @Select("SELECT * FROM bitable_base_role_permissions WHERE base_id = #{baseId} AND permission_type = #{permissionType}")
    List<BitableBaseRolePermission> selectByBaseAndType(
            @Param("baseId") Long baseId,
            @Param("permissionType") String permissionType);

    @Select("SELECT * FROM bitable_base_role_permissions WHERE base_id = #{baseId} AND table_id = #{tableId}")
    List<BitableBaseRolePermission> selectByBaseAndTable(
            @Param("baseId") Long baseId,
            @Param("tableId") Long tableId);
}
