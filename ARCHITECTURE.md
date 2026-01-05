# Architecture — Message flows and runtime behavior

Supported channel protocols: kafka | sqs | amqp | mqtt | jms

This document describes the runtime architecture and message flows of the application (not the source layout). It explains which channels/topics/queues the app listens to, what processing is performed, and where messages are forwarded after processing.

Diagram 

To make the runtime flows easier to understand, below is a compact ASCII flow that shows the main runtime path.

ASCII overview

```
+-------------------+      +----------------------+      +-------------------------+      +--------------------------+
| External Producer | ---> | Message Listener     | ---> | OrderService            | ---> | Outbound channel(s)      |
| (kafka/sqs/amqp/  |      | (transport-specific) |      | - compute total         |      | (wip/cancelled/accepted) |
|  mqtt/jms)        |      | - parse message      |      | - publish to channels   |      +--------------------------+
+-------------------+      +----------------------+      | - save state on delivery|
                                                         +-----------+-------------+
                                                                        |
                                             +--------------------------+----------------------+
                                             | In-memory DB (store for SHIPPED orders)        |
                                             +-------------------------------------------------+
```

Channels (protocols)
- Channels in this application are protocol-based. A channel may be backed by one of the following transport protocols:
  - kafka
  - sqs
  - amqp
  - mqtt
  - jms

High-level summary
- The application is an asynchronous message-driven order processor that supports channels backed by kafka, sqs, amqp, mqtt or jms. Channel selection is configurable and the application is transport-agnostic: the same logical channel names (for example `channel.new-orders`) can be mapped to different transports at runtime.
- The repository includes example/reference implementations for AMQP and MQTT to illustrate the listener/publisher pattern; the architecture itself is transport-agnostic and supports adding Kafka, AWS SQS, JMS or other transports by implementing the same contracts.
- Incoming messages (new orders, cancel requests, delivery notifications) arrive on configured channels (queues, topics or topics-like endpoints depending on transport). The app processes each message, performs lightweight business logic, and forwards processed messages to other channels or updates an in-memory store.
- The app also exposes an HTTP API used to accept an order; that HTTP call results in a notification message being published to the configured outbound channel.


OrderService responsibilities:
 - compute total amount for new orders
 - publish processed messages to outbound channels (wip-orders, cancelled-orders, accepted-orders)
 - persist/update order state in-memory on delivery events (SHIPPED)

Notes:
- The repository includes example/reference implementations for AMQP and MQTT listeners/publishers. Other protocols (Kafka, SQS, JMS) can be added and used interchangeably by implementing the listener/publisher contracts described in this document.
- Channel names are configured via application properties; the same logical channel names (e.g. `channel.new-orders`) are used regardless of the underlying transport.

How to read this document
- "Channel" refers to a transport-specific endpoint (for example a queue, topic, or topic-like endpoint) depending on the configured protocol (kafka, sqs, amqp, mqtt, jms).
- Channel names are configured via application properties (see Configuration section).

Core components (runtime roles)
- Message listeners: components that subscribe to inbound channels. The repository includes example/reference listeners for AMQP (RabbitMQ) and MQTT; the design allows adding listeners for other transports (Kafka, SQS, JMS). Which listener runs is determined via `receive.protocol` configuration or conditional activation.
- Message publishers: components that publish to outbound channels. The repository includes example AMQP and MQTT publisher implementations; adding `KafkaPublisher`, `SqsPublisher`, or `JmsPublisher` is straightforward using the `MessagePublisher` abstraction. The active publisher is selected via `send.protocol`.
- OrderService: central business logic. Receives parsed messages from listeners and performs processing (compute total amount, create/cancel/ship orders, save to in-memory DB, or publish notifications).
- HTTP controller (`/orders`): provides a PUT endpoint for sending order acceptance notifications (which the controller turns into a message published to the outbound channel), and a GET endpoint to query the in-memory store.

Protocol toggle and behavior
- The repository provides example implementations for AMQP and MQTT. The application is designed so additional transports can be added and activated by configuration.
  - `receive.protocol` — controls which receive-listener implementation is enabled (e.g., `amqp`, `mqtt`, `kafka`, `sqs`, `jms` when corresponding listeners are implemented).
  - `send.protocol` — controls which publisher implementation is enabled (e.g., `amqp`, `mqtt`, `kafka`, `sqs`, `jms` when corresponding publishers are implemented).
- The AMQP implementation uses Spring Rabbit (`RabbitTemplate`) and binds several `Queue` beans (created at startup) with the names from the configured channel properties.
- The MQTT implementation uses Eclipse Paho clients to connect, subscribe (listener) or publish (publisher) to broker URL configured via `mqtt.broker-url` and other MQTT properties.

Additional transports (Kafka / SQS / JMS)

The repository provides example AMQP and MQTT implementations to demonstrate the pattern; the application's design (MessagePublisher abstraction + listener/publisher pattern) makes it straightforward to add other transports. Below are notes and minimal examples for Kafka, AWS SQS, and JMS so the architecture doc can describe them as supported/possible transports.

Kafka (topics)
- When to use: use Kafka for high-throughput, partitioned event streaming and durable topic retention.
- How it fits: implement a `KafkaPublisher` (implements `MessagePublisher`) and a `KafkaListener` (using `@KafkaListener`) that deserialize messages and call `OrderService`.
- Conditional activation: use `@ConditionalOnProperty(name = "send.protocol", havingValue = "kafka")` and similarly for `receive.protocol`.
- Example Gradle dependency (add to `build.gradle`):

  implementation 'org.springframework.kafka:spring-kafka'

- Example properties:

  kafka.bootstrap-servers=localhost:9092
  kafka.client-id=order-api
  channel.new-orders=new-orders-topic
  channel.wip-orders=wip-orders-topic

- Notes: consider using message keys (order id) if you need partition affinity; map correlation id into message headers or payload (Kafka headers supported).

AWS SQS (queues)
- When to use: use SQS for simple, serverless queueing with built-in visibility timeouts and DLQ patterns.
- How it fits: implement an `SqsPublisher` that calls the AWS SQS client to send messages, and an `SqsListener` that polls or uses a container to receive messages, then invokes `OrderService`.
- Example Gradle dependency (AWS SDK v2):

  implementation 'software.amazon.awssdk:sqs'

- Example properties (mapping channels to SQS queue URLs or logical names):

  aws.region=us-east-1
  aws.sqs.queue.new-orders-url=https://sqs.us-east-1.amazonaws.com/123456789012/new-orders
  aws.sqs.queue.wip-orders-url=https://sqs.us-east-1.amazonaws.com/123456789012/wip-orders

- Notes: don't commit AWS credentials to the repo — use environment variables, IAM role, or credential provider chain. Use DLQs and visibility timeout configuration for retries.

JMS (ActiveMQ, Artemis, IBM MQ, etc.)
- When to use: use JMS when integrating with existing JMS brokers or when you need JMS features (transactions, selectors, durable subscriptions).
- How it fits: add `JmsPublisher` (using `JmsTemplate`) and `JmsListener` (using `@JmsListener` or message listener container) that map queue/topic names to the same logical channel names.
- Example Gradle dependency (Spring JMS + ActiveMQ client):

  implementation 'org.springframework:spring-jms'
  implementation 'org.apache.activemq:activemq-client'

- Example properties:

  jms.broker-url=tcp://localhost:61616
  jms.client-id=order-api
  channel.new-orders=queue.new-orders

- Notes: configure ConnectionFactory and, when needed, durable subscribers or transacted sessions. JMS message headers differ from other transports; include correlation ids in JMSMessage properties.

Design tips when adding transports
- Reuse the `MessagePublisher` abstraction: add `KafkaPublisher`, `SqsPublisher`, `JmsPublisher` implementing the same publish(channel, payload, correlationId) contract.
- Add listener implementations that call the same `OrderService` methods so business logic remains transport-agnostic.
- Use conditional activation (`@ConditionalOnProperty`) so new transport implementations do not interfere with existing implementations.
- Decide where correlation information lives: headers (Kafka, AMQP, JMS) or payload (MQTT, SQS when using simple clients). Add a small utility to copy headers into payload when bridging protocols.
- Add documentation and example `application.properties` or `application.yml` snippets for each transport so operators can configure them.

Appendix: quick glossary
- Listener: component that receives messages (example: AMQP listener, MQTT listener, Kafka consumer, SQS poller).
- Publisher: component that sends messages to a channel (example: AMQP publisher, MQTT publisher, Kafka producer, SQS sender).
- Channel: logical name (queue/topic) used to route messages. Specific transport mappings vary (queues, topics, or queue-like endpoints).
