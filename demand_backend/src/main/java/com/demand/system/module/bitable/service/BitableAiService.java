package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.AiBuildTableResult;
import com.demand.system.module.bitable.dto.AiQueryResult;

/**
 * 多维表格 AI 能力 Service
 */
public interface BitableAiService {

    /**
     * AI 自然语言建表（预览）
     * 根据 description 调用 LLM 生成表结构，不写入数据库
     *
     * @param description 自然语言描述
     * @return AI 生成的表结构预览
     */
    AiBuildTableResult previewBuildTable(String description);

    /**
     * AI 自然语言建表（确认写入）
     * 根据预览结果实际创建 Table + Fields + View
     *
     * @param baseId Base ID
     * @param result AI 生成的表结构
     * @param userId 创建者ID
     * @return 新数据表的 ID
     */
    Long confirmBuildTable(Long baseId, AiBuildTableResult result, Long userId);

    /**
     * AI 智能填充单个单元格
     * 读取记录上下文，调用 LLM 返回填充值，更新单元格
     *
     * @param tableId  数据表ID
     * @param recordId 记录ID
     * @param fieldId  字段ID
     * @param userId   操作人ID
     * @return 填充后的单元格值
     */
    Object fillCell(Long tableId, Long recordId, Long fieldId, Long userId);

    /**
     * AI 批量填充（异步，RabbitMQ）
     * 发送异步任务到 MQ，批量填充指定字段的所有记录
     *
     * @param tableId 数据表ID
     * @param fieldId 字段ID
     * @param userId  操作人ID
     */
    void fillBatchAsync(Long tableId, Long fieldId, Long userId);

    /**
     * AI 对话式查询
     * 根据问题调用 LLM 生成条件，在内存过滤记录（Phase 3 简化实现）
     *
     * @param baseId   Base ID
     * @param tableId  数据表ID（可选）
     * @param question 用户问题
     * @param userId   查询用户ID
     * @return 查询结果（答案 + 匹配记录列表）
     */
    AiQueryResult query(Long baseId, Long tableId, String question, Long userId);

    /**
     * AI 自动分类
     * 根据源字段文本值，使用 LLM 分类，创建新字段并批量更新
     *
     * @param tableId         数据表ID
     * @param sourceFieldId   源文本字段ID
     * @param targetFieldName 目标字段名
     * @param userId          操作人ID
     */
    void classifyRecords(Long tableId, Long sourceFieldId, String targetFieldName, Long userId);

    /**
     * AI 自动摘要
     * 根据源字段文本值，使用 LLM 生成摘要，创建新字段并批量更新
     *
     * @param tableId         数据表ID
     * @param sourceFieldId   源文本字段ID
     * @param targetFieldName 目标字段名
     * @param userId          操作人ID
     */
    void summarizeRecords(Long tableId, Long sourceFieldId, String targetFieldName, Long userId);
}