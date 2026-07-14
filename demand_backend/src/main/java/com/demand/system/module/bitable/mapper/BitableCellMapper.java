package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableCellValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-单元格值 Mapper
 */
@Mapper
public interface BitableCellMapper extends BaseMapper<BitableCellValue> {

    /**
     * 批量查询记录的所有单元格值
     *
     * @param recordIds 记录ID列表
     * @return 单元格值列表
     */
    List<BitableCellValue> selectByRecordIds(@Param("recordIds") List<Long> recordIds);

    /**
     * 查询指定记录和字段的单元格值
     *
     * @param recordId 记录ID
     * @param fieldId  字段ID
     * @return 单元格值
     */
    BitableCellValue selectByRecordAndField(@Param("recordId") Long recordId, @Param("fieldId") Long fieldId);

    /**
     * 插入或更新单元格值（依赖 record_id + field_id 唯一索引）
     * 注意：方法名不能用 insertOrUpdate，与 MyBatis-Plus BaseMapper 的同名方法冲突
     *
     * @param cell 单元格值
     * @return 影响行数
     */
    int saveOrUpdateCell(@Param("cell") BitableCellValue cell);

    /**
     * 物理删除指定记录的所有单元格值
     *
     * @param recordId 记录ID
     * @return 删除行数
     */
    int deleteByRecordId(@Param("recordId") Long recordId);
}
