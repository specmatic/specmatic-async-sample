#!/bin/bash

# Order API Test Script
# This script helps test the multi-protocol order API

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Order API Test Script${NC}"
echo "================================"
echo ""

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}Warning: jq not found. JSON formatting will be disabled.${NC}"
    JQ_AVAILABLE=false
else
    JQ_AVAILABLE=true
fi

# container name discovery
find_container() {
    local service="$1"
    local image="$2"
    local name

    # 1) Try docker-compose label (most reliable for compose-managed containers)
    name=$(docker ps --filter "label=com.docker.compose.service=${service}" --format '{{.Names}}' | head -n1 2>/dev/null || true)
    if [ -n "$name" ]; then
        echo "$name"
        return 0
    fi

    # Not found
    return 1
}

# Resolve containers used by the script
KAFKA_CONTAINER=$(find_container kafka confluentinc/cp-kafka) || { echo -e "${RED}Kafka container not found. Is docker-compose running?${NC}"; exit 1; }
MOSQUITTO_CONTAINER=$(find_container mosquitto eclipse-mosquitto) || { echo -e "${RED}Mosquitto container not found. Is docker-compose running?${NC}"; exit 1; }
ARTEMIS_CONTAINER=$(find_container artemis apache/activemq-artemis) || { echo -e "${RED}Artemis container not found. Is docker-compose running?${NC}"; exit 1; }
RABBITMQ_CONTAINER=$(find_container rabbitmq rabbitmq) || { echo -e "${RED}RabbitMQ container not found. Is docker-compose running?${NC}"; exit 1; }

# Function to test Kafka
test_kafka() {
    echo -e "${GREEN}Testing Kafka...${NC}"
    
    # Create test message
    MESSAGE='{"orderCorrelationId":"test-12345","payload":{"id":100,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'
    
    # Send message
    echo "Sending order to Kafka topic 'new-orders'..."
    echo "$MESSAGE" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
        --broker-list localhost:9092 \
        --topic new-orders
    
    echo -e "${GREEN}Message sent successfully!${NC}"
    echo "Check the application logs to see the processed order."
    echo "To consume from wip-orders topic, run:"
    echo "  docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic wip-orders --from-beginning"
}

# Function to test SQS
test_sqs() {
    echo -e "${GREEN}Testing SQS...${NC}"
    
    # Create queue if it doesn't exist
    echo "Creating queue 'new-orders' in LocalStack..."
    aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name new-orders --region us-east-1 || true
    
    # Send message
    MESSAGE='{"orderCorrelationId":"test-12345","payload":{"id":100,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'
    
    echo "Sending order to SQS queue 'new-orders'..."
    aws --endpoint-url=http://localhost:4566 sqs send-message \
        --queue-url http://localhost:4566/000000000000/new-orders \
        --message-body "$MESSAGE" \
        --message-attributes 'orderCorrelationId={StringValue=test-12345,DataType=String}' \
        --region us-east-1
    
    echo -e "${GREEN}Message sent successfully!${NC}"
    echo "Check the application logs to see the processed order."
    echo "To receive from wip-orders queue, run:"
    echo "  aws --endpoint-url=http://localhost:4566 sqs receive-message --queue-url http://localhost:4566/000000000000/wip-orders --region us-east-1"
}

# Function to test MQTT
test_mqtt() {
    echo -e "${GREEN}Testing MQTT...${NC}"
    
    MESSAGE='{"orderCorrelationId":"test-12345","payload":{"id":100,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'
    
    echo "Publishing to MQTT topic 'new-orders' on port 1884..."
    docker exec "$MOSQUITTO_CONTAINER" mosquitto_pub \
        -h localhost \
        -p 1884 \
        -t new-orders \
        -m "$MESSAGE"
    
    echo -e "${GREEN}Message published successfully!${NC}"
    echo "Check the application logs to see the processed order."
    echo "To subscribe to wip-orders topic, run:"
    echo "  docker exec -it mosquitto mosquitto_sub -h localhost -p 1884 -t wip-orders"
}

# Function to test JMS
test_jms() {
    echo -e "${GREEN}Testing JMS (Artemis)...${NC}"
    
    MESSAGE='{"orderCorrelationId":"test-12345","payload":{"id":100,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'
    
    echo "Sending message to JMS queue 'new-orders' via Artemis..."
    
    # Using artemis CLI to send message
    docker exec "$ARTEMIS_CONTAINER" /var/lib/artemis-instance/bin/artemis producer \
        --user admin \
        --password admin \
        --message-count 1 \
        --destination new-orders \
        --message "$MESSAGE" \
        --url tcp://localhost:61616
    
    echo -e "${GREEN}Message sent successfully!${NC}"
    echo "Check the application logs to see the processed order."
    echo "To consume from wip-orders queue, run:"
    echo "  docker exec artemis /var/lib/artemis-instance/bin/artemis consumer --user admin --password admin --destination wip-orders --url tcp://localhost:61616"
}

# Function to test AMQP
test_amqp() {
    echo -e "${GREEN}Testing AMQP (RabbitMQ)...${NC}"
    
    MESSAGE='{"orderCorrelationId":"test-12345","payload":{"id":100,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'
    
    echo "Publishing to RabbitMQ queue 'new-orders' on port 5673..."
    docker exec "$RABBITMQ_CONTAINER" rabbitmqadmin publish \
        exchange=amq.default \
        routing_key=new-orders \
        payload="$MESSAGE"
    
    echo -e "${GREEN}Message published successfully!${NC}"
    echo "Check the application logs to see the processed order."
    echo "To consume from wip-orders queue, run:"
    echo "  docker exec rabbitmq rabbitmqadmin get queue=wip-orders"
    echo ""
    echo "Or access RabbitMQ Management UI:"
    echo "  http://localhost:15672 (guest/guest)"
}

# Function to test Cancel Order
test_cancel_order() {
    echo -e "${GREEN}Testing Cancel Order (Kafka)...${NC}"
    
    MESSAGE='{"orderCorrelationId":"cancel-12345","payload":{"id":100}}'
    
    echo "Sending cancel order request..."
    echo "$MESSAGE" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
        --broker-list localhost:9092 \
        --topic to-be-cancelled-orders
    
    echo -e "${GREEN}Cancel request sent successfully!${NC}"
    echo "Check the application logs to see the processed cancellation."
    echo "To consume from cancelled-orders topic, run:"
    echo "  docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic cancelled-orders --from-beginning"
}

# Function to test Delivery Initiation
test_delivery() {
    echo -e "${GREEN}Testing Delivery Initiation (Kafka)...${NC}"
    
    MESSAGE='{"orderId":100,"deliveryAddress":"123 Main St, City","deliveryDate":"2026-01-15"}'
    
    echo "Sending delivery initiation..."
    echo "$MESSAGE" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
        --broker-list localhost:9092 \
        --topic out-for-delivery-orders
    
    echo -e "${GREEN}Delivery initiation sent successfully!${NC}"
    echo "Check the application logs to see the processed delivery."
}

# Main menu
echo "Select a test to run:"
echo "1) Test Kafka - Place Order"
echo "2) Test SQS - Place Order"
echo "3) Test MQTT - Place Order (port 1884)"
echo "4) Test JMS (Artemis) - Place Order"
echo "5) Test AMQP (RabbitMQ) - Place Order (port 5673)"
echo "6) Test Cancel Order (Kafka)"
echo "7) Test Delivery Initiation (Kafka)"
echo "8) Exit"
echo ""
read -p "Enter choice [1-8]: " choice

case $choice in
    1)
        test_kafka
        ;;
    2)
        test_sqs
        ;;
    3)
        test_mqtt
        ;;
    4)
        test_jms
        ;;
    5)
        test_amqp
        ;;
    6)
        test_cancel_order
        ;;
    7)
        test_delivery
        ;;
    8)
        echo "Exiting..."
        exit 0
        ;;
    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}Test completed!${NC}"
