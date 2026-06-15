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
}
