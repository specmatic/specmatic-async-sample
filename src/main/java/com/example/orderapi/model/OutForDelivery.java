package com.example.orderapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutForDelivery {
    private Integer orderId;
    private String deliveryAddress;
    private String deliveryDate;
}
