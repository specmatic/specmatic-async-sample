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

# New: determine protocols and channels from application.properties
PROPS_FILE="./src/main/resources/application.properties"

if [ ! -f "$PROPS_FILE" ]; then
    echo -e "${RED}application.properties not found at $PROPS_FILE${NC}"
    exit 1
fi

recv_protocol=$(grep -E '^\s*receive.protocol' "$PROPS_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
send_protocol=$(grep -E '^\s*send.protocol' "$PROPS_FILE" | cut -d'=' -f2 | tr -d '[:space:]')

if [ -z "$recv_protocol" ] || [ -z "$send_protocol" ]; then
    echo -e "${RED}Could not determine receive.protocol or send.protocol from $PROPS_FILE${NC}"
    exit 1
fi

# Channels used for input and output (default keys used by app)
CHANNEL_IN=$(grep -E '^\s*channel.new-orders' "$PROPS_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
CHANNEL_OUT=$(grep -E '^\s*channel.wip-orders' "$PROPS_FILE" | cut -d'=' -f2 | tr -d '[:space:]')

: "${CHANNEL_IN:=new-orders}"
: "${CHANNEL_OUT:=wip-orders}"

echo "Detected receive.protocol=$recv_protocol send.protocol=$send_protocol"
echo "Will send to channel: $CHANNEL_IN and check replies on: $CHANNEL_OUT"

# Shared test message
MESSAGE='{"orderCorrelationId":"test-12345","payload":{"id":100,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'

# Send helper (protocol -> send to input channel)
send_by_protocol() {
    local protocol="$1"
    local channel="$2"

    case "$protocol" in
        kafka)
            echo "Sending to Kafka topic '$channel'..."
            echo "$MESSAGE" | docker exec -i "$KAFKA_CONTAINER" kafka-console-producer \
                --broker-list localhost:9092 \
                --topic "$channel"
            ;;
        sqs)
            echo "Sending to SQS queue '$channel'..."
            aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name "$channel" --region us-east-1 || true
            aws --endpoint-url=http://localhost:4566 sqs send-message \
                --queue-url "http://localhost:4566/000000000000/$channel" \
                --message-body "$MESSAGE" \
                --message-attributes "orderCorrelationId={StringValue=$(echo "$MESSAGE" | jq -r .orderCorrelationId 2>/dev/null || echo auto),DataType=String}" \
                --region us-east-1
            ;;
        mqtt)
            echo "Publishing to MQTT topic '$channel'..."
            docker exec "$MOSQUITTO_CONTAINER" mosquitto_pub \
                -h localhost \
                -p 1884 \
                -t "$channel" \
                -m "$MESSAGE"
            ;;
        jms)
            echo "Sending JMS message to '$channel' (Artemis)..."
            docker exec "$ARTEMIS_CONTAINER" /var/lib/artemis-instance/bin/artemis producer \
                --user admin \
                --password admin \
                --message-count 1 \
                --destination "$channel" \
                --message "$MESSAGE" \
                --url tcp://localhost:61616
            ;;
        amqp)
            echo "Publishing to RabbitMQ queue '$channel'..."
            docker exec "$RABBITMQ_CONTAINER" rabbitmqadmin publish \
                exchange=amq.default \
                routing_key="$channel" \
                payload="$MESSAGE"
            ;;
        *)
            echo -e "${RED}Unsupported receive.protocol: $protocol${NC}"
            exit 1
            ;;
    esac
}

# Receive/check helper (protocol -> attempt to read one message from output channel)
check_reply() {
    local protocol="$1"
    local channel="$2"
    local output

    case "$protocol" in
        kafka)
            echo "Attempting to consume one message from Kafka topic '$channel'..."
            output=$(docker exec "$KAFKA_CONTAINER" bash -c "timeout 10 kafka-console-consumer --bootstrap-server localhost:9092 --topic \"$channel\" --from-beginning --max-messages 1" 2>/dev/null || true)
            ;;
        sqs)
            echo "Attempting to receive one message from SQS queue '$channel'..."
            output=$(aws --endpoint-url=http://localhost:4566 sqs receive-message \
                --queue-url "http://localhost:4566/000000000000/$channel" \
                --max-number-of-messages 1 \
                --region us-east-1 2>/dev/null || true)
            ;;
        mqtt)
            echo "Subscribing to MQTT topic '$channel' for one message..."
            output=$(timeout 10 docker exec "$MOSQUITTO_CONTAINER" mosquitto_sub -h localhost -p 1884 -t "$channel" -C 1 2>/dev/null || true)
            ;;
        jms)
            echo "Attempting to consume one JMS message from '$channel' (Artemis)..."
            output=$(docker exec "$ARTEMIS_CONTAINER" /var/lib/artemis-instance/bin/artemis consumer \
                --user admin \
                --password admin \
                --destination "$channel" \
                --message-count 1 \
                --url tcp://localhost:61616 2>/dev/null || true)
            ;;
        amqp)
            echo "Attempting to get one message from RabbitMQ queue '$channel'..."
            output=$(docker exec "$RABBITMQ_CONTAINER" rabbitmqadmin get queue="$channel" count=1 requeue=false 2>/dev/null || true)
            ;;
        *)
            echo -e "${RED}Unsupported send.protocol: $protocol${NC}"
            exit 1
            ;;
    esac

    if [ -n "$output" ]; then
        echo -e "${GREEN}Reply received on $protocol/$channel:${NC}"
        echo "$output"
        return 0
    else
        echo -e "${RED}No reply received on $protocol/$channel within timeout.${NC}"
        return 1
    fi
}

# Main automatic flow: send to receive.protocol channel, then check send.protocol channel
echo ""
echo "Sending test message to receive.protocol=$recv_protocol on channel '$CHANNEL_IN'..."
send_by_protocol "$recv_protocol" "$CHANNEL_IN"

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

echo "Checking for reply on send.protocol=$send_protocol on channel '$CHANNEL_OUT'..."
if check_reply "$send_protocol" "$CHANNEL_OUT"; then
    echo -e "${GREEN}Test succeeded: reply observed.${NC}"
    exit 0
else
    echo -e "${RED}Test failed: no reply observed.${NC}"
    exit 2
fi

echo ""
echo -e "${GREEN}Test completed!${NC}"
