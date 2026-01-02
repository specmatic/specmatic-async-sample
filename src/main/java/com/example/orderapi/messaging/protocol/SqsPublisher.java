package com.example.orderapi.messaging.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "send.protocol", havingValue = "sqs")
public class SqsPublisher implements MessagePublisher {

    private final SqsClient sqsClient;

    public SqsPublisher(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Override
    public void publish(String channel, String messagePayload, String correlationId) {
        log.info("Publishing to SQS - Queue: {}, CorrelationId: {}", channel, correlationId);
        
        try {
            String queueUrl = getQueueUrl(channel);
            
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messagePayload)
                    .messageAttributes(java.util.Map.of(
                            "orderCorrelationId",
                            MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(correlationId)
                                    .build()
                    ))
                    .build();
            
            sqsClient.sendMessage(sendMessageRequest);
            log.debug("Published to SQS successfully");
        } catch (Exception e) {
            log.error("Error publishing to SQS", e);
            throw new RuntimeException("Failed to publish to SQS", e);
        }
    }
    
    private String getQueueUrl(String queueName) {
        try {
            GetQueueUrlResponse response = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder().queueName(queueName).build()
            );
            return response.queueUrl();
        } catch (QueueDoesNotExistException e) {
            log.info("Queue {} does not exist, creating it", queueName);
            CreateQueueResponse createResponse = sqsClient.createQueue(
                    CreateQueueRequest.builder().queueName(queueName).build()
            );
            return createResponse.queueUrl();
        }
    }
}
