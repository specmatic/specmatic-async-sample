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

You can test different protocol combinations by modifying the `recieve.protocol` and `send.protocol` in `ContractTest.kt`.
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
