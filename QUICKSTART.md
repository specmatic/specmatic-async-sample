# Quick Start Guide

## 1. Start Infrastructure (30 seconds)

```bash
docker compose up -d
```

Wait for all services to start (check with `docker compose ps`)

## 2. Configure Protocol Combination

Edit `src/main/resources/application.properties` and set your desired protocols:

```properties
receive.protocol=mqtt
send.protocol=kafka
```

Supported values: `kafka`, `sqs`, `mqtt`, `jms`, `amqp`

## 3. Build & Run Application

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun
```

## 4. Run Contract Tests

```bash
./gradlew test
```

**That's it!** The contract test automatically adapts to your protocol configuration.

## 5. Test the Application Manually

### Using Test Script
```bash
./test-order-api.sh
```

### Manual Testing with Kafka

**Send a new order:**
```bash
docker exec -it kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic new-orders

# Then paste this message:
{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Macbook","quantity":1,"price":2000.0},{"id":2,"name":"iPhone","quantity":1,"price":1000.0}]}}
```

**Receive processed order:**
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic wip-orders \
  --from-beginning
```

## Available Protocol Profiles

- `kafka-kafka` - Kafka receive → Kafka send
- `kafka-sqs` - Kafka receive → SQS send
- `sqs-kafka` - SQS receive → Kafka send
- `sqs-sqs` - SQS receive → SQS send
- `mqtt-mqtt` - MQTT receive → MQTT send
- `mqtt-kafka` - MQTT receive → Kafka send
- `amqp-amqp` - RabbitMQ receive → RabbitMQ send
- `jms-jms` - ActiveMQ receive → ActiveMQ send

## Protocol Ports

| Protocol | Port | Management UI |
|----------|------|---------------|
| Kafka | 9092 | - |
| SQS (LocalStack) | 4566 | - |
| MQTT | 1883 | - |
| RabbitMQ | 5672 | http://localhost:15672 (guest/guest) |
| ActiveMQ Artemis | 61616 | http://localhost:8161 (admin/admin) |

## Troubleshooting

### Services not starting?
```bash
docker compose down -v
docker compose up -d
```

### Application won't connect?
- Check services are running: `docker compose ps`
- View service logs: `docker compose logs -f [service-name]`
- Ensure correct protocol in `application.properties`

### Port conflicts?
```bash
# Find process using port
lsof -i :9092  # or other port

# Stop conflicting service
docker compose down
```

## Cleanup

```bash
# Stop application (Ctrl+C)

# Stop all services
docker compose down -v
```

## Message Flow

1. **Place Order**: `new-orders` → Process → `wip-orders`
2. **Cancel Order**: `to-be-cancelled-orders` → Process → `cancelled-orders`
3. **Delivery**: `out-for-delivery-orders` → Process → `accepted-orders`

## Next Steps

- Modify `application.properties` to change protocols
- Check `README.md` for detailed documentation
- Explore the AsyncAPI spec at `src/main/resources/asyncapi.yaml`
