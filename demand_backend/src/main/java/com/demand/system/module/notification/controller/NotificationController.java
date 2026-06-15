package com.demand.system.module.notification.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.notification.entity.Notification;
import com.demand.system.module.notification.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<Notification>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        List<Notification> list = notificationService.listByUser(userId, pageNum, pageSize);
        PageResult<Notification> pageResult = new PageResult<>(list, list.size(), pageNum, pageSize);
        return Result.success(pageResult);
    }

    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> countUnread() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.success(0);
        }
        return Result.success(notificationService.countUnread(userId));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        // 行级校验：通知必须属于当前用户（service 内做）
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}
