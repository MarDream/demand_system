package com.demand.system.module.notification.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.notification.entity.Notification;
import com.demand.system.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
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
    public Result<Integer> countUnread() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.success(0);
        }
        return Result.success(notificationService.countUnread(userId));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        notificationService.markAllAsRead(userId);
        return Result.success();
    }
}
