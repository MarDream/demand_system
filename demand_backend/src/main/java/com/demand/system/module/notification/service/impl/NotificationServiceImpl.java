package com.demand.system.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.notification.entity.Notification;
import com.demand.system.module.notification.mapper.NotificationMapper;
import com.demand.system.module.notification.sender.NotificationSender;
import com.demand.system.module.notification.service.NotificationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSender notificationSender;

    public NotificationServiceImpl(NotificationMapper notificationMapper, NotificationSender notificationSender) {
        this.notificationMapper = notificationMapper;
        this.notificationSender = notificationSender;
    }

    @Override
    public List<Notification> listByUser(Long userId, int pageNum, int pageSize) {
        Page<Notification> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        return notificationMapper.selectPage(page, wrapper).getRecords();
    }

    @Override
    public int countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    public void markAsRead(Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null) {
            notification.setIsRead(1);
            notificationMapper.updateById(notification);
        }
    }

    @Override
    public void markAsRead(Long notificationId, Long currentUserId) {
        if (notificationId == null || currentUserId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数缺失");
        }
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (!Objects.equals(notification.getUserId(), currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该通知");
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return; // 已读幂等
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    @Override
    public void markAllAsRead(Long userId) {
        Notification notification = new Notification();
        notification.setIsRead(1);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0);
        notificationMapper.update(notification, wrapper);
    }

    @Override
    public void sendNotification(Long userId, String title, String content, String type, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }
}
