package com.demand.system.module.notification.sender;

import com.demand.system.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSender {

    private final RabbitTemplate rabbitTemplate;

    public void sendSystemNotification(Long userId, String title, String content) {
        sendNotification(userId, title, content, "system");
    }

    public void sendRequirementNotification(Long userId, String title, String content) {
        sendNotification(userId, title, content, "requirement");
    }

    private void sendNotification(Long userId, String title, String content, String type) {
        Map<String, Object> message = new HashMap<>();
        message.put("userId", userId);
        message.put("title", title);
        message.put("content", content);
        message.put("type", type);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "notification." + type, message);
        log.info("Notification sent to user {}: {}", userId, title);
    }
}
