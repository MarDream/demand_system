package com.demand.system.module.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.assistant.dto.ExtractedQuestionVO;
import com.demand.system.module.assistant.dto.QuickQuestionVO;
import com.demand.system.module.assistant.entity.QuickQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuickQuestionMapper extends BaseMapper<QuickQuestion> {

    /**
     * 前台查询：按人工优先 → AI 补齐规则返回最多 3 条
     * 规则：先取人工维护(manual_curated)的启用问题，不足 3 条时用 AI 自动提炼(auto_extracted)补齐
     */
    List<QuickQuestionVO> selectForFrontend(@Param("pageRoute") String pageRoute, @Param("limit") int limit);

    /**
     * AI 提炼：从最近 N 天的 question_logs 中按频次聚合高频问题
     */
    List<ExtractedQuestionVO> selectExtractedFromLogs(@Param("windowDays") int windowDays, @Param("minFrequency") int minFrequency);

    /**
     * 增加点击次数
     */
    int incrementHitCount(@Param("id") Long id);
}
