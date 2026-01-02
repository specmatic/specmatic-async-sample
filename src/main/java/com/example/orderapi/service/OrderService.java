package com.example.orderapi.service;

import com.example.orderapi.model.*;
import com.example.orderapi.messaging.protocol.MessagePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    @Autowired(required = false)
    private MessagePublisher messagePublisher;

    @Value("${channel.wip-orders}")
    private String wipOrdersChannel;

    @Value("${channel.cancelled-orders}")
    private String cancelledOrdersChannel;

    @Value("${channel.accepted-orders}")
    private String acceptedOrdersChannel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void processNewOrder(OrderRequest orderRequest, String correlationId) {
        log.info("Processing new order: {}", orderRequest.getId());
        
        try {
            double totalAmount = orderRequest.getOrderItems().stream()
                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                    .sum();
            
            Order order = new Order(orderRequest.getId(), totalAmount, OrderStatus.INITIATED);
            
            MessageWrapper<Order> wrapper = new MessageWrapper<>(correlationId, order);
            String message = objectMapper.writeValueAsString(wrapper);
            
            if (messagePublisher != null) {
                messagePublisher.publish(wipOrdersChannel, message, correlationId);
                log.info("Order {} initiated with total amount: {}", order.getId(), totalAmount);
            }
        } catch (Exception e) {
            log.error("Error processing new order", e);
            throw new RuntimeException("Failed to process new order", e);
        }
    }

    public void processCancelOrder(CancelOrderRequest cancelRequest, String correlationId) {
        log.info("Processing cancel order request: {}", cancelRequest.getId());
        
        try {
            CancellationReference cancellationRef = new CancellationReference(
                    cancelRequest.getId(), 
                    OrderStatus.CANCELLED
            );
            
            MessageWrapper<CancellationReference> wrapper = new MessageWrapper<>(correlationId, cancellationRef);
            String message = objectMapper.writeValueAsString(wrapper);
            
            if (messagePublisher != null) {
                messagePublisher.publish(cancelledOrdersChannel, message, correlationId);
                log.info("Order {} cancelled", cancelRequest.getId());
            }
        } catch (Exception e) {
            log.error("Error processing cancel order", e);
            throw new RuntimeException("Failed to process cancel order", e);
        }
    }

    public void processOrderDelivery(OutForDelivery deliveryInfo) {
        log.info("Processing order delivery initiation for order: {}", deliveryInfo.getOrderId());
        
        try {
            OrderAccepted orderAccepted = new OrderAccepted(
                    deliveryInfo.getOrderId(),
                    OrderStatus.SHIPPED,
                    Instant.now().toString()
            );
            
            String correlationId = UUID.randomUUID().toString();
            String message = objectMapper.writeValueAsString(orderAccepted);
            
            if (messagePublisher != null) {
                messagePublisher.publish(acceptedOrdersChannel, message, correlationId);
                log.info("Order {} marked as shipped", deliveryInfo.getOrderId());
            }
        } catch (Exception e) {
            log.error("Error processing order delivery", e);
            throw new RuntimeException("Failed to process order delivery", e);
        }
    }
}
