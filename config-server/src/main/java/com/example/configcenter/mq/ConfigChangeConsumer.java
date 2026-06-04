package com.example.configcenter.mq;

import com.example.configcenter.config.RabbitMQConfig;
import com.example.configcenter.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(RabbitMQConfig.class)
public class ConfigChangeConsumer {

    private final NotificationService notificationService;

    public ConfigChangeConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "#{configChangeQueue.name}")
    public void handleConfigChange(ConfigChangeMessage message) {
        notificationService.notifyChange(message.getEnvironment(), message.getNamespace());
    }
}
