package com.example.orderapi.controller;

import com.example.orderapi.model.Order;
import com.example.orderapi.model.OrderAccepted;
import com.example.orderapi.model.OrderStatus;
import com.example.orderapi.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderWith(
            @PathVariable Integer id,
            @RequestParam String status
    ) {
        log.info("Received request to find Order with id '{}' and status '{}'", id, status);
        
        Order order = orderService.getOrder(id);
        
        if (order == null) {
            log.warn("Order with id '{}' not found", id);
            return ResponseEntity.notFound().build();
        }
        
        if (!order.getStatus().name().equals(status)) {
            log.warn("Order with id '{}' has status '{}', expected '{}'", id, order.getStatus(), status);
            return ResponseEntity.notFound().build();
        }
        
        log.info("Found order: {}", order);
        return ResponseEntity.ok(order);
    }

    @PutMapping
    public ResponseEntity<String> updateOrder(@RequestBody OrderUpdateRequest request) {
        log.info("Received update request: {}", request);
        
        try {
            OrderAccepted orderAccepted = new OrderAccepted(
                    request.getId(),
                    request.getStatus(),
                    request.getTimestamp()
            );
            
            orderService.acceptOrder(orderAccepted);
            log.info("Order {} update notification sent", request.getId());
            return ResponseEntity.ok("Notification triggered.");
        } catch (JsonProcessingException e) {
            log.error("Error processing update request", e);
            return ResponseEntity.internalServerError().body("Failed to process update");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderUpdateRequest {
        private Integer id;
        private OrderStatus status;
        private String timestamp;
    }
}
