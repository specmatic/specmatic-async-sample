package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "jms")
public class JmsRetryPublisher implements RetryMessagePublisher {

    private final JmsTemplate jmsTemplate;

    public JmsRetryPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing retry message to JMS - Queue: {}, CorrelationId: {}", channel, correlationId);

        jmsTemplate.convertAndSend(channel, messagePayload, message -> {
            message.setStringProperty("orderCorrelationId", correlationId);
            return message;
        });

        log.debug("Published retry message to JMS successfully");
    }
}

