package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableCommentVO;
import com.demand.system.module.bitable.entity.BitableComment;
import com.demand.system.module.bitable.mapper.BitableCommentMapper;
import com.demand.system.module.bitable.service.BitableCommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多维表格-行级评论 Service 实现
 */
@Service
public class BitableCommentServiceImpl implements BitableCommentService {

    private final BitableCommentMapper commentMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;

    public BitableCommentServiceImpl(BitableCommentMapper commentMapper,
                                     BitableConverter converter,
                                     UserNameResolver userNameResolver) {
        this.commentMapper = commentMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
    }

    @Override
    public List<BitableCommentVO> listComments(Long recordId) {
        List<BitableComment> comments = commentMapper.selectByRecordId(recordId);
        List<BitableCommentVO> voList = converter.toCommentVOList(comments);
        for (BitableCommentVO vo : voList) {
            vo.setUserName(userNameResolver.resolveUserName(vo.getUserId(), "未知用户"));
            // avatar 暂时不填充，留到后续扩展
            vo.setAvatar(null);
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createComment(Long recordId, Long tableId, String content, Long quoteFieldId, Long parentId, Long userId) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }

        BitableComment comment = new BitableComment();
        comment.setRecordId(recordId);
        comment.setTableId(tableId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setQuoteFieldId(quoteFieldId);
        comment.setParentId(parentId);
        commentMapper.insert(comment);

        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id) {
        BitableComment existing = commentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("评论不存在");
        }
        commentMapper.deleteById(id);
    }
}