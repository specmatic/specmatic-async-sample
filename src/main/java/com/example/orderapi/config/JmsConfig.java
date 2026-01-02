package com.example.orderapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;

@Configuration
@EnableJms
@ConditionalOnExpression("'${receive.protocol}'.equals('jms') or '${send.protocol}'.equals('jms')")
public class JmsConfig {
}
