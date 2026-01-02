package com.example.orderapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderAccepted {
    private Integer id;
    private OrderStatus status;
    private String timestamp;
}
