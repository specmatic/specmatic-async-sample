package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "mqtt")
public class MqttRetryPublisher implements RetryMessagePublisher {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.qos}")
    private int qos;

    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId + "-retry-publisher");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            mqttClient.connect(options);
            log.info("MQTT Retry Publisher connected to broker: {}", brokerUrl);
        } catch (MqttException e) {
            log.error("Error connecting MQTT Retry Publisher", e);
            throw new RuntimeException("Failed to connect MQTT Retry Publisher", e);
        }
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing retry message to MQTT - Topic: {}, CorrelationId: {}", channel, correlationId);

        try {
            MqttMessage message = new MqttMessage(messagePayload.getBytes());
            message.setQos(qos);
            mqttClient.publish(channel, message);
            log.debug("Published retry message to MQTT successfully");
        } catch (MqttException e) {
            log.error("Error publishing retry message to MQTT", e);
            throw new RuntimeException("Failed to publish retry message to MQTT", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException e) {
            log.error("Error disconnecting MQTT Retry Publisher", e);
        }
    }
}

