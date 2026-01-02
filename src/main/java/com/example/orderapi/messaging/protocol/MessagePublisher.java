package com.example.orderapi.messaging.protocol;

public interface MessagePublisher {
    void publish(String channel, String message, String correlationId);
}
