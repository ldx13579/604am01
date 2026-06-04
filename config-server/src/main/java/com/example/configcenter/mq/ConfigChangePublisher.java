package com.example.configcenter.mq;

import com.example.configcenter.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(RabbitMQConfig.class)
public class ConfigChangePublisher {

    private final RabbitTemplate rabbitTemplate;

    public ConfigChangePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishChange(String environment, String namespace, Long version, String operation) {
        ConfigChangeMessage message = new ConfigChangeMessage(environment, namespace, version, operation);
        rabbitTemplate.convertAndSend(RabbitMQConfig.CONFIG_CHANGE_EXCHANGE, "", message);
    }
}
