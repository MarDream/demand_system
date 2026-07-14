package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-协作成员权限 Mapper
 */
@Mapper
public interface BitableBaseMemberMapper extends BaseMapper<BitableBaseMember> {

    /**
     * 查询指定多维表格容器的所有成员
     *
     * @param baseId 多维表格容器ID
     * @return 成员列表
     */
    List<BitableBaseMember> selectByBaseId(@Param("baseId") Long baseId);

    /**
     * 查询指定用户在指定多维表格容器中的成员记录
     *
     * @param baseId 多维表格容器ID
     * @param userId 用户ID
     * @return 成员记录
     */
    BitableBaseMember selectByBaseAndUser(@Param("baseId") Long baseId, @Param("userId") Long userId);

    /**
     * 删除指定多维表格容器的所有成员
     *
     * @param baseId 多维表格容器ID
     * @return 删除行数
     */
    int deleteByBaseId(@Param("baseId") Long baseId);
}
