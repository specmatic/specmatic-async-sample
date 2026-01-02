package com.example.orderapi.messaging.listener;

import com.example.orderapi.model.*;
import com.example.orderapi.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "kafka")
public class KafkaMessageListener {

    @Autowired
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "${channel.new-orders}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleNewOrder(@Payload String message, @Header(value = "orderCorrelationId", required = false) String correlationId) {
        log.info("Received new order from Kafka - CorrelationId: {}", correlationId);
        try {
            OrderRequest orderRequest = objectMapper.readValue(message, OrderRequest.class);
            orderService.processNewOrder(orderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing new order from Kafka", e);
        }
    }

    @KafkaListener(topics = "${channel.to-be-cancelled-orders}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCancelOrder(@Payload String message, @Header(value = "orderCorrelationId", required = false) String correlationId) {
        log.info("Received cancel order from Kafka - CorrelationId: {}", correlationId);
        try {
            CancelOrderRequest cancelOrderRequest = objectMapper.readValue(message, CancelOrderRequest.class);
            orderService.processCancelOrder(cancelOrderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing cancel order from Kafka", e);
        }
    }

    @KafkaListener(topics = "${channel.out-for-delivery-orders}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleOrderDelivery(@Payload String message) {
        log.info("Received order delivery from Kafka");
        try {
            OutForDelivery deliveryInfo = objectMapper.readValue(message, OutForDelivery.class);
            orderService.processOrderDelivery(deliveryInfo);
        } catch (Exception e) {
            log.error("Error processing order delivery from Kafka", e);
        }
    }
}
