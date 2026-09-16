package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableFieldPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 多维表格-角色字段权限 Mapper
 */
@Mapper
public interface BitableFieldPermissionMapper extends BaseMapper<BitableFieldPermission> {

    /** 查询某个 Base 下的全部字段权限配置（字段权限面板一次性加载） */
    @Select("SELECT * FROM bitable_field_permissions WHERE base_id = #{baseId}")
    List<BitableFieldPermission> selectByBaseId(@Param("baseId") Long baseId);

    /** 查询某张表的全部字段权限配置（渲染记录时按当前用户解析生效级别） */
    @Select("SELECT * FROM bitable_field_permissions WHERE table_id = #{tableId}")
    List<BitableFieldPermission> selectByTableId(@Param("tableId") Long tableId);

    /**
     * 删除某个字段的全部字段权限配置。
     * <p>
     * 表上没有外键，删除字段时不会级联，必须显式清理，
     * 否则会留下指向已删字段的孤儿权限行（唯一键还会挡住同角色再次配置）。
     */
    @Delete("DELETE FROM bitable_field_permissions WHERE field_id = #{fieldId}")
    int deleteByFieldId(@Param("fieldId") Long fieldId);

    /** 删除某张表下全部字段权限配置（删除数据表时调用） */
    @Delete("DELETE FROM bitable_field_permissions WHERE table_id = #{tableId}")
    int deleteByTableId(@Param("tableId") Long tableId);
}
