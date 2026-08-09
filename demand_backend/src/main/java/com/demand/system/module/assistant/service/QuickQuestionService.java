package com.demand.system.module.assistant.service;

import com.demand.system.module.assistant.dto.ExtractedQuestionVO;
import com.demand.system.module.assistant.dto.QuickQuestionCreateDTO;
import com.demand.system.module.assistant.dto.QuickQuestionVO;

import java.util.List;

public interface QuickQuestionService {

    /**
     * 前台查询：人工优先 + AI 补齐，每页面最多 3 条
     */
    List<QuickQuestionVO> getForFrontend(String pageRoute);

    /**
     * 后台查询全部（分页）
     */
    List<QuickQuestionVO> listAll(String pageRoute, String status, String category);

    /**
     * AI 提炼（从埋点日志聚合）
     */
    List<ExtractedQuestionVO> getExtracted(int windowDays, int minFrequency);

    /**
     * 创建/编辑（后台管理）
     */
    QuickQuestionVO create(QuickQuestionCreateDTO dto);

    /**
     * 更新
     */
    QuickQuestionVO update(Long id, QuickQuestionCreateDTO dto);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 更新状态（启用/停用）
     */
    void toggleStatus(Long id, String status);

    /**
     * 采纳 AI 提炼建议（从埋点日志聚合而来，无 quick_questions.id）
     * 同文本已存在则转为人工维护并启用；不存在则创建人工维护记录
     */
    QuickQuestionVO adoptAiSuggestion(String questionText, String pageRoute, String questionHash);

    /**
     * 记录点击
     */
    void recordClick(Long id);
}
