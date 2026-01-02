package com.example.orderapi.service;

import com.example.orderapi.model.*;
import com.example.orderapi.messaging.protocol.MessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrderService {

    @Autowired(required = false)
    private MessagePublisher messagePublisher;

    private final ConcurrentHashMap<Integer, Order> orderDatabase = new ConcurrentHashMap<>();

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
            
            String payload = objectMapper.writeValueAsString(order);
            
            if (messagePublisher != null) {
                messagePublisher.publish(wipOrdersChannel, payload, correlationId);
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
            String payload = objectMapper.writeValueAsString(cancellationRef);
            
            if (messagePublisher != null) {
                messagePublisher.publish(cancelledOrdersChannel, payload, correlationId);
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
            Order order = new Order(deliveryInfo.getOrderId(), null, OrderStatus.SHIPPED);
            saveOrder(deliveryInfo.getOrderId(), order);

            log.info("Order {} marked as shipped and saved to database", deliveryInfo.getOrderId());
        } catch (Exception e) {
            log.error("Error processing order delivery", e);
            throw new RuntimeException("Failed to process order delivery", e);
        }
    }

    public void acceptOrder(OrderAccepted orderAccepted) throws JsonProcessingException {
        messagePublisher.publish(
                acceptedOrdersChannel,
                objectMapper.writeValueAsString(orderAccepted),
                "12345"
        );
        log.info("Order {} has been accepted", orderAccepted.getId());
    }

    public void saveOrder(Integer orderId, Order order) {
        log.info("Saving order {} to in-memory database", orderId);
        orderDatabase.put(orderId, order);
    }

    public Order getOrder(Integer id) {
        return orderDatabase.get(id);
    }
}
