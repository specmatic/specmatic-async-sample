# Order API - Multi-Protocol AsyncAPI Implementation

A Spring Boot application that implements an AsyncAPI specification with support for multiple messaging protocols (Kafka, SQS, MQTT, JMS, AMQP).

## Features

- **Multi-Protocol Support**: Switch between different messaging protocols without code changes
- **Flexible Protocol Combinations**: Receive on one protocol and send on another (e.g., Kafka → SQS)
- **Supported Protocols**:
  - Apache Kafka
  - AWS SQS (via LocalStack)
  - MQTT (Eclipse Mosquitto)
  - JMS (ActiveMQ Artemis)
  - AMQP (RabbitMQ)

## Architecture

The application implements the following AsyncAPI operations:

### Receive Operations (Consumer)
1. **placeOrder** - Receives new order requests on `new-orders` channel
   - Calculates total amount and publishes to `wip-orders`
2. **cancelOrder** - Receives cancellation requests on `to-be-cancelled-orders` channel
   - Processes cancellation and publishes to `cancelled-orders`
3. **initiateOrderDelivery** - Receives delivery info on `out-for-delivery-orders` channel
   - Publishes order acceptance to `accepted-orders`

### Send Operations (Producer)
- Publishes processed orders to respective channels based on configured protocol

## Project Structure

```
order-api/
├── src/main/java/com/example/orderapi/
│   ├── model/                          # Domain models
│   │   ├── OrderRequest.java
│   │   ├── Order.java
│   │   ├── CancelOrderRequest.java
│   │   ├── CancellationReference.java
│   │   ├── OrderAccepted.java
│   │   ├── OutForDelivery.java
│   │   └── OrderStatus.java
│   ├── config/                         # Protocol configurations
│   │   ├── KafkaConfig.java
│   │   ├── SqsConfig.java
│   │   ├── AmqpConfig.java
│   │   └── JmsConfig.java
│   ├── messaging/
│   │   ├── protocol/                   # Protocol implementations
│   │   │   ├── MessagePublisher.java
│   │   │   ├── KafkaPublisher.java
│   │   │   ├── SqsPublisher.java
│   │   │   ├── MqttPublisher.java
│   │   │   ├── JmsPublisher.java
│   │   │   └── AmqpPublisher.java
│   │   └── listener/                   # Protocol listeners
│   │       ├── KafkaMessageListener.java
│   │       ├── SqsMessageListener.java
│   │       ├── MqttMessageListener.java
│   │       ├── JmsMessageListener.java
│   │       └── AmqpMessageListener.java
│   ├── service/
│   │   └── OrderService.java          # Business logic
│   └── OrderApiApplication.java
└── src/main/resources/
    ├── asyncapi.yaml                   # AsyncAPI specification
    ├── application.properties          # Default config
    └── application-{protocol}.properties # Protocol-specific configs
```

## Prerequisites

- Java 17 or higher
- Docker and Docker Compose

## Quick Start

### 1. Start Infrastructure Services

Start all messaging brokers using Docker Compose:

```bash
docker compose up -d
```

This starts:
- **Kafka** on port 9092
- **Zookeeper** on port 2181
- **LocalStack (SQS)** on port 4566
- **Mosquitto (MQTT)** on port 1883
- **ActiveMQ Artemis (JMS)** on ports 61616, 8161
- **RabbitMQ (AMQP)** on ports 5672, 15672

### 2. Build the Application

```bash
./gradlew clean build
```

### 3. Run the Application

#### Option A: Using specific protocol profile

```bash
# Kafka to Kafka
./gradlew bootRun --args="--spring.profiles.active=kafka-kafka"

# Kafka to SQS
./gradlew bootRun --args="--spring.profiles.active=kafka-sqs"

# SQS to SQS
./gradlew bootRun --args="--spring.profiles.active=sqs-sqs"

# MQTT to MQTT
./gradlew bootRun --args="--spring.profiles.active=mqtt-mqtt"

# AMQP to AMQP
./gradlew bootRun --args="--spring.profiles.active=amqp-amqp"

# JMS to JMS
./gradlew bootRun --args="--spring.profiles.active=jms-jms"
```

#### Option B: Modify application.properties

Edit `src/main/resources/application.properties` and set:

```properties
receive.protocol=kafka  # or sqs, mqtt, jms, amqp
send.protocol=sqs       # or kafka, mqtt, jms, amqp
```

Then run:

```bash
./gradlew bootRun
```

## Testing the Application

### Using Kafka

**Producer (send message):**
```bash
docker exec -it kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic new-orders \
  --property "parse.key=true" \
  --property "key.separator=:"

# Then type:
1:{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Macbook","quantity":1,"price":2000.0},{"id":2,"name":"iPhone","quantity":1,"price":1000.0}]}}
```

**Consumer (receive messages):**
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic wip-orders \
  --from-beginning
```

### Using SQS (LocalStack)

**Create queue and send message:**
```bash
# Create queue
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name new-orders

# Send message
aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/new-orders \
  --message-body '{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Macbook","quantity":1,"price":2000.0}]}}' \
  --message-attributes 'orderCorrelationId={StringValue=12345,DataType=String}'

# Receive messages
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/wip-orders
```

### Using MQTT

**Publish message:**
```bash
docker exec -it mosquitto mosquitto_pub \
  -h localhost \
  -t new-orders \
  -m '{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Macbook","quantity":1,"price":2000.0}]}}'
```

**Subscribe to messages:**
```bash
docker exec -it mosquitto mosquitto_sub \
  -h localhost \
  -t wip-orders
```

### Using RabbitMQ (AMQP)

Access RabbitMQ Management UI: http://localhost:15672 (guest/guest)

Or use CLI:
```bash
# Publish
docker exec -it rabbitmq rabbitmqadmin publish \
  routing_key=new-orders \
  payload='{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Macbook","quantity":1,"price":2000.0}]}}'

# Consume
docker exec -it rabbitmq rabbitmqadmin get queue=wip-orders
```

### Using JMS (ActiveMQ Artemis)

Access Artemis Console: http://localhost:8161 (admin/admin)

## Protocol Configuration

### Kafka
```properties
receive.protocol=kafka
send.protocol=kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=order-api-group
```

### AWS SQS
```properties
receive.protocol=sqs
send.protocol=sqs
spring.cloud.aws.endpoint=http://localhost:4566
spring.cloud.aws.region.static=us-east-1
```

### MQTT
```properties
receive.protocol=mqtt
send.protocol=mqtt
mqtt.broker-url=tcp://localhost:1883
mqtt.client-id=order-api-client
mqtt.qos=1
```

### JMS
```properties
receive.protocol=jms
send.protocol=jms
spring.artemis.host=localhost
spring.artemis.port=61616
spring.artemis.user=admin
spring.artemis.password=admin
```

### AMQP
```properties
receive.protocol=amqp
send.protocol=amqp
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

## Message Examples

### Order Request
```json
{
  "orderCorrelationId": "12345",
  "payload": {
    "id": 10,
    "orderItems": [
      {
        "id": 1,
        "name": "Macbook",
        "quantity": 1,
        "price": 2000.0
      },
      {
        "id": 2,
        "name": "iPhone",
        "quantity": 1,
        "price": 1000.0
      }
    ]
  }
}
```

### Cancel Order Request
```json
{
  "orderCorrelationId": "12345",
  "payload": {
    "id": 10
  }
}
```

### Out For Delivery
```json
{
  "orderId": 10,
  "deliveryAddress": "1234 Elm Street, Springfield",
  "deliveryDate": "2025-04-14"
}
```

## Monitoring

- **Kafka**: Use Kafka Console Consumer or tools like Conduktor
- **SQS**: AWS CLI with LocalStack endpoint
- **MQTT**: mosquitto_sub or MQTT Explorer
- **RabbitMQ**: Management UI at http://localhost:15672
- **ActiveMQ Artemis**: Console at http://localhost:8161

## Troubleshooting

### Port Already in Use
```bash
# Check what's using the port
lsof -i :9092  # or other port

# Stop all services
docker compose down
```

### Cannot Connect to Broker
```bash
# Check if services are running
docker compose ps

# View logs
docker compose logs -f kafka
docker compose logs -f localstack
docker compose logs -f mosquitto
docker compose logs -f rabbitmq
docker compose logs -f artemis
```

### Application Won't Start
- Ensure all required services are running
- Check application logs for specific errors
- Verify protocol configuration in application.properties

## Cleanup

Stop all services:
```bash
docker compose down -v
```

## License

MIT

## Author

Generated for AsyncAPI multi-protocol demonstration
