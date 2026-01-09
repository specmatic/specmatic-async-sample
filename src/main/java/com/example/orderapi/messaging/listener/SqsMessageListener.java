package com.example.orderapi.messaging.listener;

import com.example.orderapi.model.*;
import com.example.orderapi.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "receive.protocol", havingValue = "sqs")
public class SqsMessageListener {

    @Autowired
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SqsListener("${channel.new-orders}")
    public void handleNewOrder(Message<String> message) {
        log.info("Received new order from SQS");
        try {
            String payload = message.getPayload();
            OrderRequest orderRequest = objectMapper.readValue(payload, OrderRequest.class);
            String correlationId = (String) message.getHeaders().get("orderCorrelationId");

            orderService.processNewOrder(orderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing new order from SQS", e);
        }
    }

    @SqsListener("${channel.to-be-cancelled-orders}")
    public void handleCancelOrder(Message<String> message) {
        log.info("Received cancel order from SQS");
        try {
            String payload = message.getPayload();
            CancelOrderRequest cancelOrderRequest = objectMapper.readValue(payload, CancelOrderRequest.class);
            String correlationId = (String) message.getHeaders().get("orderCorrelationId");

            orderService.processCancelOrder(cancelOrderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing cancel order from SQS", e);
        }
    }

    @SqsListener("${channel.retry-failed-cancelled-orders}")
    public void handleRetryFailedCancelOrder(Message<String> message) {
        log.info("Received retry failed cancel order from SQS");
        try {
            String payload = message.getPayload();
            CancelOrderRequest cancelOrderRequest = objectMapper.readValue(payload, CancelOrderRequest.class);
            String correlationId = (String) message.getHeaders().get("orderCorrelationId");

            orderService.processRetryFailedCancelOrder(cancelOrderRequest, correlationId);
        } catch (Exception e) {
            log.error("Error processing retry failed cancel order from SQS", e);
        }
    }

    @SqsListener("${channel.out-for-delivery-orders}")
    public void handleOrderDelivery(String payload) {
        log.info("Received order delivery from SQS");
        try {
            OutForDelivery deliveryInfo = objectMapper.readValue(payload, OutForDelivery.class);
            orderService.processOrderDelivery(deliveryInfo);
        } catch (Exception e) {
            log.error("Error processing order delivery from SQS", e);
        }
    }
}
