package com.example.orderapi.messaging.listener;

import com.example.orderapi.model.*;
import com.example.orderapi.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "jms")
public class JmsMessageListener {

    @Autowired
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @JmsListener(destination = "${channel.new-orders}")
    public void handleNewOrder(Message message) {
        log.info("Received new order from JMS");
        try {
            String payload = extractPayload(message);
            String correlationId = message.getStringProperty("orderCorrelationId");

            log.debug("Payload: {}", payload);
            OrderRequest orderRequest = objectMapper.readValue(payload, OrderRequest.class);

            orderService.processNewOrder(orderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing new order from JMS", e);
        }
    }

    @JmsListener(destination = "${channel.to-be-cancelled-orders}")
    public void handleCancelOrder(Message message) {
        log.info("Received cancel order from JMS");
        try {
            String payload = extractPayload(message);
            String correlationId = message.getStringProperty("orderCorrelationId");

            log.debug("Payload: {}", payload);
            CancelOrderRequest cancelOrderRequest = objectMapper.readValue(payload, CancelOrderRequest.class);

            orderService.processCancelOrder(cancelOrderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing cancel order from JMS", e);
        }
    }

    @JmsListener(destination = "${channel.out-for-delivery-orders}")
    public void handleOrderDelivery(Message message) {
        log.info("Received order delivery from JMS");
        try {
            String payload = extractPayload(message);

            log.debug("Payload: {}", payload);
            OutForDelivery deliveryInfo = objectMapper.readValue(payload, OutForDelivery.class);
            orderService.processOrderDelivery(deliveryInfo);
        } catch (Exception e) {
            log.error("Error processing order delivery from JMS", e);
        }
    }

    private String extractPayload(Message message) throws JMSException {
        if (message instanceof TextMessage) {
            return ((TextMessage) message).getText();
        } else if (message instanceof BytesMessage bytesMessage) {
            byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(bytes);
            return new String(bytes);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.getClass().getName());
        }
    }
}
