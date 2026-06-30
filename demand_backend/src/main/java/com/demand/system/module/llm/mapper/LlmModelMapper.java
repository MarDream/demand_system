package com.demand.system.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.llm.entity.LlmModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LlmModelMapper extends BaseMapper<LlmModel> {

    /**
     * 查找默认的 Chat 模型（isDefault=true 且 enabled=true 且 modelType 不是 embedding/rerank）。
     * 按更新时间倒序取第一条。
     */
    @Select("""
            SELECT * FROM llm_models
            WHERE is_default = 1
              AND enabled = 1
              AND model_type NOT IN ('embedding', 'rerank')
            ORDER BY updated_at DESC
            LIMIT 1
            """)
    LlmModel selectDefaultChatModel();
}
