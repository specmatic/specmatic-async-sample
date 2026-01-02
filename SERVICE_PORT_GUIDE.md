# Service Port Allocation - Simplified Setup

## ✅ All Services Run Together - No Conflicts!

All messaging brokers can now run simultaneously with a single command:
```bash
docker compose up -d
```

## Port Allocation

| Service | Port(s) | Protocol | Notes |
|---------|---------|----------|-------|
| **Kafka** | 9092 | Kafka | - |
| **Zookeeper** | 2181 | Zookeeper | Kafka coordination |
| **LocalStack (SQS)** | 4566 | SQS/HTTP | AWS SQS emulation |
| **Mosquitto (MQTT)** | **1884** | MQTT | **Changed from 1883** |
| **RabbitMQ (AMQP)** | **5673**, 15672 | AMQP, Web UI | **Changed from 5672** |
| **Artemis (JMS)** | 61616, 5672, 1883, 8161 | JMS, AMQP, MQTT, Web UI | Uses original ports |

## Port Changes Made

To avoid conflicts, we changed:
- ✅ **Mosquitto**: 1883 → **1884** (Artemis uses 1883 for MQTT)
- ✅ **RabbitMQ**: 5672 → **5673** (Artemis uses 5672 for AMQP)

## Simple Usage

### Start Everything
```bash
docker compose up -d
```

### Run Any Protocol
```bash
# Kafka
./gradlew bootRun --args="--spring.profiles.active=kafka-kafka"

# SQS
./gradlew bootRun --args="--spring.profiles.active=sqs-sqs"

# JMS (Artemis)
./gradlew bootRun --args="--spring.profiles.active=jms-jms"

# MQTT (Mosquitto on port 1884)
./gradlew bootRun --args="--spring.profiles.active=mqtt-mqtt"

# AMQP (RabbitMQ on port 5673)
./gradlew bootRun --args="--spring.profiles.active=amqp-amqp"
```

### Stop Everything
```bash
docker compose down
```

## Verification

**Check all services are running:**
```bash
docker compose ps
```

**Test connectivity:**
```bash
nc -zv localhost 2181   # Zookeeper
nc -zv localhost 4566   # LocalStack
nc -zv localhost 1884   # Mosquitto (MQTT)
nc -zv localhost 5673   # RabbitMQ (AMQP)
nc -zv localhost 8161   # Artemis Web UI
nc -zv localhost 9092   # Kafka
nc -zv localhost 15672  # RabbitMQ Web UI
nc -zv localhost 61616  # Artemis (JMS)
```

## Management UIs

| Service | URL | Credentials |
|---------|-----|-------------|
| Artemis | http://localhost:8161 | admin / admin |
| RabbitMQ | http://localhost:15672 | guest / guest |

## No Conditional Starting Required!

Unlike before, you don't need to:
- ❌ Stop Mosquitto before starting Artemis
- ❌ Start specific services for specific protocols
- ❌ Remember which services conflict

Just run:
```bash
docker compose up -d
```

All services will start and work together! 🎉

## Quick Reference

### Testing Each Protocol

**Kafka:**
```bash
# Services already running from: docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=kafka-kafka"
```

**SQS:**
```bash
# Services already running from: docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=sqs-sqs"
```

**JMS (Artemis on port 61616):**
```bash
# Services already running from: docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=jms-jms"
```

**MQTT (Mosquitto on port 1884):**
```bash
# Services already running from: docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=mqtt-mqtt"
```

**AMQP (RabbitMQ on port 5673):**
```bash
# Services already running from: docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=amqp-amqp"
```

---

**That's it! Simple and straightforward!** 🚀
