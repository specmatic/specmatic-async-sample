package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "send.protocol", havingValue = "ibmmq")
public class IbmMqPublisher implements MessagePublisher {

    private final JmsTemplate jmsTemplate;

    public IbmMqPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing to IBM MQ - Queue: {}, CorrelationId: {}", channel, correlationId);

        jmsTemplate.convertAndSend(channel, messagePayload, message -> {
            message.setStringProperty("orderCorrelationId", correlationId);
            return message;
        });

        log.debug("Published to IBM MQ successfully");
    }
}
