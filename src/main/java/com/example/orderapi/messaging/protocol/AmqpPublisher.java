package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "send.protocol", havingValue = "amqp")
public class AmqpPublisher implements MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public AmqpPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing to AMQP - Queue: {}, CorrelationId: {}", channel, correlationId);
        
        Message message = MessageBuilder.withBody(messagePayload.getBytes())
                .setHeader("orderCorrelationId", correlationId)
                .build();
        
        rabbitTemplate.send(channel, message);
        log.debug("Published to AMQP successfully");
    }
}
