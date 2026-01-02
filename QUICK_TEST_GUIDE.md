# Quick Test Guide

## Fixed: Auto-Configuration Issue

The SQS connection error has been fixed! Each profile now only loads the protocols it needs.

## Testing Different Protocols

### 1. Kafka to Kafka (Easiest to Test)

**Start Kafka:**
```bash
docker compose up -d kafka zookeeper
```

**Wait for Kafka:**
```bash
sleep 15
```

**Run Application:**
```bash
./gradlew bootRun --args="--spring.profiles.active=kafka-kafka"
```

**Send Test Message:**
```bash
# In another terminal
docker exec -it kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic new-orders

# Paste this message:
{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}
```

**Receive Response:**
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic wip-orders \
  --from-beginning
```

---

### 2. SQS to SQS

**Start LocalStack:**
```bash
docker compose up -d localstack
```

**Wait for LocalStack:**
```bash
sleep 10
```

**Run Application:**
```bash
./gradlew bootRun --args="--spring.profiles.active=sqs-sqs"
```

**Send Test Message:**
```bash
# Create queue and send message
aws --endpoint-url=http://localhost:4566 sqs send-message \
  --queue-url http://localhost:4566/000000000000/new-orders \
  --message-body '{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}' \
  --region us-east-1
```

**Receive Response:**
```bash
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/wip-orders \
  --region us-east-1
```

---

### 3. MQTT to MQTT

**Start Mosquitto:**
```bash
docker compose up -d mosquitto
```

**Run Application:**
```bash
./gradlew bootRun --args="--spring.profiles.active=mqtt-mqtt"
```

**Send Test Message:**
```bash
docker exec mosquitto mosquitto_pub \
  -h localhost \
  -t new-orders \
  -m '{"orderCorrelationId":"12345","payload":{"id":10,"orderItems":[{"id":1,"name":"Laptop","quantity":1,"price":1500.0}]}}'
```

**Receive Response:**
```bash
docker exec -it mosquitto mosquitto_sub \
  -h localhost \
  -t wip-orders
```

---

### 4. AMQP to AMQP (RabbitMQ)

**Start RabbitMQ:**
```bash
docker compose up -d rabbitmq
```

**Wait for RabbitMQ:**
```bash
sleep 15
```

**Run Application:**
```bash
./gradlew bootRun --args="--spring.profiles.active=amqp-amqp"
```

**Management UI:**
```
http://localhost:15672
Username: guest
Password: guest
```

---

### 5. JMS to JMS (ActiveMQ Artemis)

**Start Artemis:**
```bash
docker compose up -d artemis
```

**Wait for Artemis:**
```bash
sleep 20
```

**Run Application:**
```bash
./gradlew bootRun --args="--spring.profiles.active=jms-jms"
```

**Management UI:**
```
http://localhost:8161
Username: admin
Password: admin
```

---

## Using the Start Script

**Interactive Mode:**
```bash
./start.sh
```

This will:
1. Check prerequisites
2. Start Docker services
3. Build the application
4. Let you choose a protocol
5. Run the application

---

## Stopping Services

**Stop all:**
```bash
docker compose down
```

**Stop with volume cleanup:**
```bash
docker compose down -v
```

**Stop specific service:**
```bash
docker compose stop kafka
```

---

## Troubleshooting

**Check if service is running:**
```bash
docker compose ps
```

**View logs:**
```bash
docker compose logs -f kafka
docker compose logs -f localstack
docker compose logs -f mosquitto
```

**Check ports:**
```bash
lsof -i :9092   # Kafka
lsof -i :4566   # LocalStack
lsof -i :1883   # Mosquitto
lsof -i :5672   # RabbitMQ
lsof -i :61616  # Artemis
```

---

## Quick Commands Reference

| Protocol | Start Service | Run App |
|----------|---------------|---------|
| Kafka | `docker compose up -d kafka zookeeper` | `--spring.profiles.active=kafka-kafka` |
| SQS | `docker compose up -d localstack` | `--spring.profiles.active=sqs-sqs` |
| MQTT | `docker compose up -d mosquitto` | `--spring.profiles.active=mqtt-mqtt` |
| AMQP | `docker compose up -d rabbitmq` | `--spring.profiles.active=amqp-amqp` |
| JMS | `docker compose up -d artemis` | `--spring.profiles.active=jms-jms` |

---

## What's Fixed

✅ Each profile only loads required protocols  
✅ No connection errors to unused services  
✅ Faster startup  
✅ Cleaner logs  
✅ Auto-configuration exclusions working  

---

Happy Testing! 🚀
