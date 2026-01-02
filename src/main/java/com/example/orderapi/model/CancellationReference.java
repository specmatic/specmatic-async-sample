package com.example.orderapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancellationReference {
    private Integer reference;
    private OrderStatus status;
}
