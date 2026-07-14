package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableCommentVO;

import java.util.List;

/**
 * 多维表格-行级评论 Service
 */
public interface BitableCommentService {

    /**
     * 列出记录的所有评论
     *
     * @param recordId 记录ID
     * @return 评论列表
     */
    List<BitableCommentVO> listComments(Long recordId);

    /**
     * 创建评论
     *
     * @param recordId     记录ID
     * @param tableId      数据表ID
     * @param content      评论内容
     * @param quoteFieldId 引用字段ID（可为 null）
     * @param parentId     父评论ID（可为 null）
     * @param userId       创建者ID
     * @return 新评论的 ID
     */
    Long createComment(Long recordId, Long tableId, String content, Long quoteFieldId, Long parentId, Long userId);

    /**
     * 删除评论
     *
     * @param id 评论ID
     */
    void deleteComment(Long id);
}