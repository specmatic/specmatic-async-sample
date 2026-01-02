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
            MessageWrapper<OrderRequest> wrapper = objectMapper.readValue(payload,
                objectMapper.getTypeFactory().constructParametricType(MessageWrapper.class, OrderRequest.class));
            
            String correlationId = (String) message.getHeaders().get("orderCorrelationId");
            if (correlationId == null) {
                correlationId = wrapper.getOrderCorrelationId();
            }
            
            orderService.processNewOrder(wrapper.getPayload(), correlationId);
        } catch (Exception e) {
            log.error("Error processing new order from SQS", e);
        }
    }

    @SqsListener("${channel.to-be-cancelled-orders}")
    public void handleCancelOrder(Message<String> message) {
        log.info("Received cancel order from SQS");
        try {
            String payload = message.getPayload();
            MessageWrapper<CancelOrderRequest> wrapper = objectMapper.readValue(payload,
                objectMapper.getTypeFactory().constructParametricType(MessageWrapper.class, CancelOrderRequest.class));
            
            String correlationId = (String) message.getHeaders().get("orderCorrelationId");
            if (correlationId == null) {
                correlationId = wrapper.getOrderCorrelationId();
            }
            
            orderService.processCancelOrder(wrapper.getPayload(), correlationId);
        } catch (Exception e) {
            log.error("Error processing cancel order from SQS", e);
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
