package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "kafka")
public class KafkaRetryPublisher implements RetryMessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaRetryPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing retry message to Kafka - Channel: {}, CorrelationId: {}", channel, correlationId);

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
        log.debug("Published retry message to Kafka successfully");
    }
}

