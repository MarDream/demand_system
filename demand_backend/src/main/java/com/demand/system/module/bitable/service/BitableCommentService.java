package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableCommentVO;
import com.demand.system.module.bitable.entity.BitableComment;

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
     * @param content      评论内容
     * @param quoteFieldId 引用字段ID（可为 null）
     * @param parentId     父评论ID（可为 null）
     * @param userId       创建者ID
     * @return 新评论的 ID
     */
    Long createComment(Long recordId, String content, Long quoteFieldId, Long parentId, Long userId);

    /**
     * 按 ID 获取评论实体（供权限判断使用）
     *
     * @param id 评论ID
     * @return 评论实体，不存在返回 null
     */
    BitableComment getCommentById(Long id);

    /**
     * 删除评论
     *
     * @param id 评论ID
     */
    void deleteComment(Long id);
}
