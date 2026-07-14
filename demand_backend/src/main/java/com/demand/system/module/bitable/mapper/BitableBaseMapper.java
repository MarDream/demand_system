package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格容器 Mapper
 */
@Mapper
public interface BitableBaseMapper extends BaseMapper<BitableBase> {

    /**
     * 查询某用户创建的所有多维表格容器
     *
     * @param userId 用户ID
     * @return 多维表格容器列表
     */
    List<BitableBase> selectByCreator(@Param("userId") Long userId);

    /**
     * 查询某用户是成员的所有多维表格容器
     *
     * @param userId 用户ID
     * @return 多维表格容器列表
     */
    List<BitableBase> selectByMember(@Param("userId") Long userId);

    /**
     * 查询多维表格容器详情，包含表数量聚合
     *
     * @param id 容器ID
     * @return 多维表格容器详情
     */
    BitableBase selectDetailById(@Param("id") Long id);
}
