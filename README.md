# Overview

This project showcases the flexibility of specmatic-async when working with different combinations of send and receive protocols.

By passing protocol values at test runtime, you can run same contract tests across multiple protocol pairings such as Kafka, SQS, JMS, MQTT and AMQP (RabbitMQ/ActiveMQ).

Give it a try to experience how specmatic-async makes protocol-agnostic asynchronous contract testing simple and scalable.

To understand the architecture of the application in detail, refer to [ARCHITECTURE.md](./ARCHITECTURE.md)

## Running Specmatic Async Contract Tests

### Prerequisites
- Docker
- Java

Default combo:

```bash
./gradlew test
```

This runs with default test protocols:

```text
receive.protocol=sqs
send.protocol=kafka
```

To run same contract test with different protocol combinations, pass JVM system properties to Gradle.

Supported values: `kafka`, `sqs`, `mqtt`, `jms`, `amqp`

```bash
./gradlew test -Dreceive.protocol=jms -Dsend.protocol=mqtt
```

More examples:

```bash
./gradlew test -Dreceive.protocol=kafka -Dsend.protocol=amqp
./gradlew test -Dreceive.protocol=mqtt -Dsend.protocol=sqs
./gradlew test -Dreceive.protocol=amqp -Dsend.protocol=jms
```

Testcontainers starts only infra needed for selected `receive.protocol` and `send.protocol`.

### Test Report
Once the tests have run, you can view the Specmatic test reports generated at: [build/reports/specmatic/async/test/html/index.html](build/reports/specmatic/async/test/html/index.html)

## Starting the application for local testing

### 1. Start Infrastructure (30 seconds)

```bash
docker compose up -d
```

Wait for all services to start (check with `docker compose ps`)

### 2. Configure Protocol Combination

Edit [src/main/resources/application.properties](src/main/resources/application.properties) and set your desired protocols:

```properties
receive.protocol=mqtt
send.protocol=kafka
```

Supported values: `kafka`, `sqs`, `mqtt`, `jms`, `amqp`

### 3. Run Application

```bash
./gradlew bootRun
```

### 4. Test Application
You can test the application by using the provided shell script as follows:

```bash
./test-order-api.sh
```
