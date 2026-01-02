package com.example.orderapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ConditionalOnExpression("'${receive.protocol}'.equals('kafka') or '${send.protocol}'.equals('kafka')")
public class KafkaConfig {
}
