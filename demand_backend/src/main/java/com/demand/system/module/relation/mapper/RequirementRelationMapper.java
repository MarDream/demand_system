package com.demand.system.module.relation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.relation.entity.RequirementRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface RequirementRelationMapper extends BaseMapper<RequirementRelation> {

    /**
     * 查询需求关联及目标需求信息
     *
     * @param sourceId 源需求ID
     * @return 包含目标需求信息的关联列表
     */
    List<Map<String, Object>> selectWithTarget(@Param("sourceId") Long sourceId);

    /**
     * 检查需求关联是否存在
     *
     * @param sourceId 源需求ID
     * @param targetId 目标需求ID
     * @param relationType 关联类型
     * @return 存在的记录数
     */
    int exists(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId, @Param("relationType") String relationType);
}
