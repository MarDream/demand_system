package com.demand.system.module.notification.service;

import com.demand.system.module.notification.entity.Notification;

import java.util.List;

public interface NotificationService {

    List<Notification> listByUser(Long userId, int pageNum, int pageSize);

    int countUnread(Long userId);

    void markAsRead(Long notificationId);

    void markAllAsRead(Long userId);

    void sendNotification(Long userId, String title, String content, String type, Long relatedId);
}
