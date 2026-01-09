package com.example.orderapi.messaging.listener;

import com.example.orderapi.model.*;
import com.example.orderapi.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "mqtt")
public class MqttMessageListener {

    @Autowired
    private OrderService orderService;

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${channel.new-orders}")
    private String newOrdersChannel;

    @Value("${channel.to-be-cancelled-orders}")
    private String cancelOrdersChannel;

    @Value("${channel.retry-failed-cancelled-orders}")
    private String retryFailedCancelOrdersChannel;

    @Value("${channel.out-for-delivery-orders}")
    private String deliveryOrdersChannel;

    private MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(brokerUrl, clientId + "-listener");
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("MQTT connection lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            
            mqttClient.connect(options);
            
            mqttClient.subscribe(newOrdersChannel);
            mqttClient.subscribe(cancelOrdersChannel);
            mqttClient.subscribe(retryFailedCancelOrdersChannel);
            mqttClient.subscribe(deliveryOrdersChannel);
            
            log.info("MQTT Listener connected and subscribed to topics");
        } catch (MqttException e) {
            log.error("Error connecting MQTT Listener", e);
            throw new RuntimeException("Failed to connect MQTT Listener", e);
        }
    }

    private void handleMessage(String topic, String payload) {
        log.info("Received message from MQTT - Topic: {}", topic);
        try {
            if (topic.equals(newOrdersChannel)) {
                OrderRequest orderRequest = objectMapper.readValue(payload, OrderRequest.class);
                orderService.processNewOrder(orderRequest, "");
            } else if (topic.equals(cancelOrdersChannel)) {
                CancelOrderRequest cancelOrderRequest = objectMapper.readValue(payload, CancelOrderRequest.class);
                orderService.processCancelOrder(cancelOrderRequest, "");
            } else if (topic.equals(retryFailedCancelOrdersChannel)) {
                CancelOrderRequest cancelOrderRequest = objectMapper.readValue(payload, CancelOrderRequest.class);
                orderService.processRetryFailedCancelOrder(cancelOrderRequest, "");
            } else if (topic.equals(deliveryOrdersChannel)) {
                OutForDelivery deliveryInfo = objectMapper.readValue(payload, OutForDelivery.class);
                orderService.processOrderDelivery(deliveryInfo);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT message", e);
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
            log.error("Error disconnecting MQTT Listener", e);
        }
    }
}
