package com.example.orderapi.messaging.protocol;

public interface MessageReceiver {
    void startListening();
    void stopListening();
}
