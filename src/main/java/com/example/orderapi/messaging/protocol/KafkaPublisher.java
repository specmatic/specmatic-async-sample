package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

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

        kafkaTemplate.send(
                new ProducerRecord<>(
                        channel,
                        0,
                        0L,
                        "",
                        messagePayload,
                        List.of(new RecordHeader("orderCorrelationId", correlationId.getBytes()))
                )
        );
        log.debug("Published to Kafka successfully");
    }
}
