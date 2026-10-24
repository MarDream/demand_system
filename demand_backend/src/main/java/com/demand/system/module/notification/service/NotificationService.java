package com.demand.system.module.notification.service;

import com.demand.system.module.notification.entity.Notification;

import java.util.List;

public interface NotificationService {

    List<Notification> listByUser(Long userId, int pageNum, int pageSize);

    int countUnread(Long userId);

    void markAsRead(Long notificationId);

    /** 行级校验：通知必须属于当前用户，否则抛 FORBIDDEN。仅标记当前用户未读的通知。 */
    void markAsRead(Long notificationId, Long currentUserId);

    void markAllAsRead(Long userId);

    void sendNotification(Long userId, String title, String content, String type, Long relatedId);

    /**
     * 发送通知（带关联评论ID，用于@提醒点击跳转到工单评论处）。
     */
    void sendNotification(Long userId, String title, String content, String type, Long relatedId, Long commentId);
}
