package com.example.orderapi.messaging.protocol;

public interface RetryMessagePublisher {
    void publish(String channel, String message, String correlationId);
}

