package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-行级评论 Mapper
 */
@Mapper
public interface BitableCommentMapper extends BaseMapper<BitableComment> {

    /**
     * 查询指定记录的所有未删除评论
     *
     * @param recordId 记录ID
     * @return 评论列表
     */
    List<BitableComment> selectByRecordId(@Param("recordId") Long recordId);

    /**
     * 查询指定数据表下的所有未删除评论
     *
     * @param tableId 数据表ID
     * @return 评论列表
     */
    List<BitableComment> selectByTableId(@Param("tableId") Long tableId);
}
