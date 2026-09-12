package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.constant.MemberRole;
import com.demand.system.module.bitable.dto.BitableCommentVO;
import com.demand.system.module.bitable.entity.BitableComment;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableCommentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多维表格评论控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableCommentController {

    /** 评论内容长度上限 */
    private static final int MAX_CONTENT_LENGTH = 5000;

    private final BitableCommentService bitableCommentService;
    private final BitableAuthorizationService authorizationService;

    public BitableCommentController(BitableCommentService bitableCommentService,
                                    BitableAuthorizationService authorizationService) {
        this.bitableCommentService = bitableCommentService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/records/{recordId}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableCommentVO>> listComments(@PathVariable Long recordId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByRecordId(recordId);
        authorizationService.checkReadPermission(baseId, userId);
        List<BitableCommentVO> list = bitableCommentService.listComments(recordId);
        return Result.success(list);
    }

    /**
     * 创建评论：COMMENTER 及以上角色即可评论；tableId 由记录反查，不信任请求体
     */
    @PostMapping("/records/{recordId}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createComment(@PathVariable Long recordId, @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            return Result.fail("评论内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Result.fail("评论内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByRecordId(recordId);
        authorizationService.checkPermission(baseId, userId, MemberRole.COMMENTER);
        Long quoteFieldId = parseLong(body.get("quoteFieldId"));
        Long parentId = parseLong(body.get("parentId"));
        Long id = bitableCommentService.createComment(recordId, content, quoteFieldId, parentId, userId);
        return Result.success(id);
    }

    /**
     * 删除评论：作者本人或 ADMIN 及以上角色
     */
    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteComment(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByCommentId(id);
        if (baseId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限操作该评论");
        }
        BitableComment comment = bitableCommentService.getCommentById(id);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            // 非作者需要 ADMIN 及以上角色才能删除他人评论
            authorizationService.checkManagePermission(baseId, userId);
        }
        bitableCommentService.deleteComment(id);
        return Result.success();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
