package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableCommentVO;
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

    private final BitableCommentService bitableCommentService;

    public BitableCommentController(BitableCommentService bitableCommentService) {
        this.bitableCommentService = bitableCommentService;
    }

    @GetMapping("/records/{recordId}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableCommentVO>> listComments(@PathVariable Long recordId) {
        List<BitableCommentVO> list = bitableCommentService.listComments(recordId);
        return Result.success(list);
    }

    @PostMapping("/records/{recordId}/comments")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createComment(@PathVariable Long recordId, @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            return Result.fail("评论内容不能为空");
        }
        Long tableId = parseLong(body.get("tableId"));
        if (tableId == null) {
            return Result.fail("tableId 不能为空");
        }
        Long quoteFieldId = parseLong(body.get("quoteFieldId"));
        Long parentId = parseLong(body.get("parentId"));
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = bitableCommentService.createComment(recordId, tableId, content, quoteFieldId, parentId, userId);
        return Result.success(id);
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteComment(@PathVariable Long id) {
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
