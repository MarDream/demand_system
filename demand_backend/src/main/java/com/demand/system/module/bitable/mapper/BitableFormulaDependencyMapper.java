package com.demand.system.module.bitable.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.bitable.entity.BitableFormulaDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 多维表格-公式依赖关系 Mapper
 */
@Mapper
public interface BitableFormulaDependencyMapper extends BaseMapper<BitableFormulaDependency> {

    /**
     * 查询指定公式字段的所有依赖
     *
     * @param formulaFieldId 公式字段ID
     * @return 依赖列表
     */
    List<BitableFormulaDependency> selectByFormulaFieldId(@Param("formulaFieldId") Long formulaFieldId);

    /**
     * 查询依赖指定字段的所有公式字段ID
     *
     * @param dependencyFieldId 被依赖的字段ID
     * @return 公式字段ID列表
     */
    List<Long> selectFormulaFieldIdsByDependencyFieldId(@Param("dependencyFieldId") Long dependencyFieldId);

    /**
     * 删除指定公式字段的所有依赖
     *
     * @param formulaFieldId 公式字段ID
     * @return 删除行数
     */
    int deleteByFormulaFieldId(@Param("formulaFieldId") Long formulaFieldId);

    /**
     * 批量插入依赖关系
     *
     * @param dependencies 依赖列表
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<BitableFormulaDependency> dependencies);

    /**
     * 查询所有公式依赖关系（用于全局循环检测）
     *
     * @return 所有依赖关系
     */
    List<BitableFormulaDependency> selectAllDependencies();
}
