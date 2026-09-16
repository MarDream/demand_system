package com.demand.system.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.llm.entity.LlmApplicationGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LLM 功能点模型应用分组 Mapper
 * <p>
 * 分组通过 {@code parentId} 自关联形成目录树，树结构由 Service 组装。
 */
@Mapper
public interface LlmApplicationGroupMapper extends BaseMapper<LlmApplicationGroup> {

    /**
     * 查询全部分组（扁平结构，未删除）
     *
     * @return 分组列表，按排序号与ID升序
     */
    List<LlmApplicationGroup> selectAllGroups();

    /**
     * 计算某父级下已有分组的最大排序号，空则为 0
     *
     * @param parentId 父分组ID，可为 null（根层级）
     * @return 最大排序号
     */
    int selectMaxSortOrder(@Param("parentId") Long parentId);
}
