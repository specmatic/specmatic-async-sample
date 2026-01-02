package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "send.protocol", havingValue = "kafka")
public class KafkaPublisher implements MessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing to Kafka - Channel: {}, CorrelationId: {}", channel, correlationId);
        
        Message<String> message = MessageBuilder
                .withPayload(messagePayload)
                .setHeader(KafkaHeaders.TOPIC, channel)
                .setHeader("orderCorrelationId", correlationId)
                .build();
        
        kafkaTemplate.send(message);
        log.debug("Published to Kafka successfully");
    }
}
