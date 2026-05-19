package com.demand.system.module.notification.consumer;

import com.demand.system.module.notification.entity.Notification;
import com.demand.system.module.notification.mapper.NotificationMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationConsumer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationMapper notificationMapper;

    public NotificationConsumer(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @RabbitListener(queues = "notification.system.queue")
    public void handleNotification(Map<String, Object> message) {
        try {
            Long userId = Long.valueOf(message.get("userId").toString());
            String title = message.get("title").toString();
            String content = message.get("content").toString();
            String type = message.get("type").toString();

            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setType(type);
            notification.setIsRead(0);

            notificationMapper.insert(notification);
            log.info("Notification saved for user {}: {}", userId, title);
        } catch (Exception e) {
            log.error("Failed to save notification: {}", message, e);
        }
    }
}
