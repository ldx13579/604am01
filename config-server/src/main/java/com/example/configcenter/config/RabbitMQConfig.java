package com.example.configcenter.config;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMQConfig {

    public static final String CONFIG_CHANGE_EXCHANGE = "config.change.fanout";

    @Bean
    public FanoutExchange configChangeExchange() {
        return new FanoutExchange(CONFIG_CHANGE_EXCHANGE);
    }

    @Bean
    public Queue configChangeQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding configChangeBinding(Queue configChangeQueue, FanoutExchange configChangeExchange) {
        return BindingBuilder.bind(configChangeQueue).to(configChangeExchange);
    }
}
