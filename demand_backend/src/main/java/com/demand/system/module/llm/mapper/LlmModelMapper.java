package com.demand.system.module.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.llm.entity.LlmModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmModelMapper extends BaseMapper<LlmModel> {

    /**
     * 查找默认的 Chat 模型（isDefault=true 且 enabled=true 且 modelType 不是 embedding/rerank）。
     * 按更新时间倒序取第一条。
     */
    LlmModel selectDefaultChatModel();
}
