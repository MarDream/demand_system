package com.demand.system.module.requirement.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequirementTypeMapper extends BaseMapper<RequirementTypeConfig> {

    /**
     * 按类型编码查询配置。
     * <p>用于 {@code WorkflowVersionResolver.resolveForType} 按 type 反查工作流版本绑定关系。
     *
     * @param code 类型编码（如 {@code FEATURE} / {@code Order} / {@code Bug}），必传
     * @return 类型配置；code 不存在时返回 null
     */
    default RequirementTypeConfig selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<RequirementTypeConfig>()
                .eq(RequirementTypeConfig::getCode, code)
                .last("LIMIT 1"));
    }
}