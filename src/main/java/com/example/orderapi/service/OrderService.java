package com.example.orderapi.service;

import com.example.orderapi.model.*;
import com.example.orderapi.messaging.protocol.MessagePublisher;
import com.example.orderapi.messaging.protocol.RetryMessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderService {

    @Autowired(required = false)
    private MessagePublisher messagePublisher;

    @Autowired(required = false)
    private RetryMessagePublisher retryMessagePublisher;

    private final ConcurrentHashMap<Integer, Order> orderDatabase = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Value("${channel.wip-orders}")
    private String wipOrdersChannel;

    @Value("${channel.cancelled-orders}")
    private String cancelledOrdersChannel;

    @Value("${channel.retry-failed-cancelled-orders}")
    private String retryFailedCancelledOrdersChannel;

    @Value("${channel.accepted-orders}")
    private String acceptedOrdersChannel;

    @Value("${retry.cancel-order.enabled:true}")
    private boolean cancelOrderRetryEnabled;

    @Value("${retry.cancel-order.max-attempts:3}")
    private int cancelOrderMaxAttempts;

    @Value("${retry.cancel-order.initial-delay-seconds:5}")
    private int cancelOrderInitialDelaySeconds;

    @Value("${retry.cancel-order.multiplier:2}")
    private int cancelOrderMultiplier;

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
            // Check if this is a retry scenario (order id 999 triggers retry)
            boolean shouldSimulateFailure = cancelRequest.getId() == 999 && cancelOrderRetryEnabled;

            if (shouldSimulateFailure) {
                // Simulate initial failure - send to retry topic instead of cancelled-orders
                log.info("Simulating initial failure for order {}, scheduling async retries to retry topic", cancelRequest.getId());
                String retryPayload = objectMapper.writeValueAsString(cancelRequest);
                scheduleAsyncRetries(retryFailedCancelledOrdersChannel, retryPayload, correlationId);
            } else {
                // Normal flow - publish cancellation reference to cancelled-orders
                CancellationReference cancellationRef = new CancellationReference(
                        cancelRequest.getId(),
                        OrderStatus.CANCELLED
                );
                String payload = objectMapper.writeValueAsString(cancellationRef);

                if (messagePublisher != null) {
                    messagePublisher.publish(cancelledOrdersChannel, payload, correlationId);
                    log.info("Order {} cancelled", cancelRequest.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing cancel order", e);
            throw new RuntimeException("Failed to process cancel order", e);
        }
    }

    public void processRetryFailedCancelOrder(CancelOrderRequest cancelRequest, String correlationId) {
        log.info("Processing retry failed cancel order request: {}", cancelRequest.getId());

        try {
            // Process the retry - this time successfully
            CancellationReference cancellationRef = new CancellationReference(
                    cancelRequest.getId(),
                    OrderStatus.CANCELLED
            );
            String payload = objectMapper.writeValueAsString(cancellationRef);

            if (messagePublisher != null) {
                messagePublisher.publish(cancelledOrdersChannel, payload, correlationId);
                log.info("Retry successful - Order {} cancelled", cancelRequest.getId());
            }
        } catch (Exception e) {
            log.error("Error processing retry failed cancel order", e);
            throw new RuntimeException("Failed to process retry failed cancel order", e);
        }
    }

    private void scheduleAsyncRetries(String channel, String payload, String correlationId) {
        long initialDelayMillis = cancelOrderInitialDelaySeconds * 1000L;
        // Add buffer time to ensure Specmatic's subscriber is ready before publishing
        long bufferMillis = 2000L;

        for (int attempt = 1; attempt <= cancelOrderMaxAttempts; attempt++) {
            final int currentAttempt = attempt;

            // Calculate delay using exponential backoff
            // Specmatic waits for: initialDelay * multiplier^(attempt-1) before listening
            // We add buffer time to ensure it's listening before we publish
            long delayMillis = (long) (initialDelayMillis * Math.pow(cancelOrderMultiplier, attempt - 1)) + bufferMillis;

            scheduler.schedule(() -> {
                try {
                    log.info("Async retry attempt {} for order cancellation on channel: {} (scheduled at {}ms)",
                            currentAttempt, channel, delayMillis);

                    // Fail the first 2 attempts to ensure Specmatic starts listening first
                    if (currentAttempt < 3) {
                        log.warn("Simulating failure on async attempt {}", currentAttempt);
                        return; // Don't publish
                    }

                    if (currentAttempt == 3) {
                        // Publish on the 3rd attempt using RetryMessagePublisher (same protocol as receive)
                        if (retryMessagePublisher != null) {
                            retryMessagePublisher.publish(channel, payload, correlationId);
                            log.info("Order cancellation retry message published successfully on async attempt {}", currentAttempt);
                        } else {
                            log.warn("RetryMessagePublisher is null, cannot publish retry message");
                        }
                    }

                } catch (Exception e) {
                    log.error("Error on async retry attempt {}", currentAttempt, e);
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
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
