package com.example.orderapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Configuration
@ConditionalOnExpression("'${receive.protocol}'.equals('amqp') or '${send.protocol}'.equals('amqp')")
public class AmqpConfig {

    @Value("${channel.new-orders}")
    private String newOrdersChannel;

    @Value("${channel.wip-orders}")
    private String wipOrdersChannel;

    @Value("${channel.to-be-cancelled-orders}")
    private String cancelOrdersChannel;

    @Value("${channel.retry-failed-cancelled-orders}")
    private String retryFailedCancelOrdersChannel;

    @Value("${channel.cancelled-orders}")
    private String cancelledOrdersChannel;

    @Value("${channel.accepted-orders}")
    private String acceptedOrdersChannel;

    @Value("${channel.out-for-delivery-orders}")
    private String deliveryOrdersChannel;

    @Bean
    public Queue newOrdersQueue() {
        return new Queue(newOrdersChannel, false);
    }

    @Bean
    public Queue wipOrdersQueue() {
        return new Queue(wipOrdersChannel, false);
    }

    @Bean
    public Queue cancelOrdersQueue() {
        return new Queue(cancelOrdersChannel, false);
    }

    @Bean
    public Queue retryFailedCancelOrdersQueue() {
        return new Queue(retryFailedCancelOrdersChannel, false);
    }

    @Bean
    public Queue cancelledOrdersQueue() {
        return new Queue(cancelledOrdersChannel, false);
    }

    @Bean
    public Queue acceptedOrdersQueue() {
        return new Queue(acceptedOrdersChannel, false);
    }

    @Bean
    public Queue deliveryOrdersQueue() {
        return new Queue(deliveryOrdersChannel, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // Don't set a message converter - use default SimpleMessageConverter
        // This allows String payloads to be sent/received as-is
        return new RabbitTemplate(connectionFactory);
    }
}
