# Overview

This project showcases the flexibility of specmatic-async when working with different combinations of send and receive protocols.

By updating just a few lines in the ContractTest (described below), you can run the same contract tests across multiple protocol pairings such as Kafka, SQS, JMS and MQTT.

Give it a try to experience how specmatic-async makes protocol-agnostic asynchronous contract testing simple and scalable.

## Running Specmatic Async Contract Tests

```bash
./gradlew test
```

**That's it!** The contract test automatically adapts to your protocol configuration.

You can test different protocol combinations by modifying the `recieve.protocol` and `send.protocol` in `ContractTest.kt`.

Supported values: `kafka`, `sqs`, `mqtt`, `jms`

```shell
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    // Note - Update these to try out different protocol combinations
    properties = [
        "receive.protocol=jms",
        "send.protocol=mqtt"
    ]
)
```

## Starting the application for local testing

### 1. Start Infrastructure (30 seconds)

```bash
docker compose up -d
```

Wait for all services to start (check with `docker compose ps`)

### 2. Configure Protocol Combination

Edit `src/main/resources/application.properties` and set your desired protocols:

```properties
receive.protocol=mqtt
send.protocol=kafka
```

Supported values: `kafka`, `sqs`, `mqtt`, `jms`

### 3. Build & Run Application

```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun
```

