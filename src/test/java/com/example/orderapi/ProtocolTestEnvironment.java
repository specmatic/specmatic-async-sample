package com.example.orderapi;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

final class ProtocolTestEnvironment {
    private static final DockerImageName KAFKA_IMAGE = DockerImageName.parse("apache/kafka:3.8.0");
    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.13");
    private static final DockerImageName RABBIT_IMAGE = DockerImageName.parse("rabbitmq:3-management");
    private static final DockerImageName ARTEMIS_IMAGE = DockerImageName.parse("apache/activemq-artemis:latest");
    private static final DockerImageName MOSQUITTO_IMAGE = DockerImageName.parse("eclipse-mosquitto:2.0");
    private static final String KAFKA_ALIAS = "kafka-broker";
    private static final int KAFKA_INTERNAL_PORT = 19092;
    private static final String SQS_ALIAS = "localstack-sqs";
    private static final int SQS_INTERNAL_PORT = 4566;
    private static final String MQTT_ALIAS = "mqtt-broker";
    private static final int MQTT_INTERNAL_PORT = 1884;
    private static final String JMS_ALIAS = "artemis-broker";
    private static final int JMS_INTERNAL_PORT = 61616;
    private static final String AMQP_ALIAS = "rabbitmq-broker";
    private static final int AMQP_INTERNAL_PORT = 5672;

    private static final List<String> QUEUES = List.of(
            "new-orders",
            "wip-orders",
            "to-be-cancelled-orders",
            "cancelled-orders",
            "accepted-orders",
            "out-for-delivery-orders"
    );

    private final String receiveProtocol;
    private final String sendProtocol;
    private final String runId;
    private final Network network;
    private final KafkaContainer kafka;
    private final LocalStackContainer localstack;
    private final RabbitMQContainer rabbitmq;
    private final GenericContainer<?> artemis;
    private final GenericContainer<?> mosquitto;

    private ProtocolTestEnvironment(String receiveProtocol, String sendProtocol) {
        this.receiveProtocol = receiveProtocol;
        this.sendProtocol = sendProtocol;
        this.runId = UUID.randomUUID().toString();
        this.network = Network.newNetwork();
        this.kafka = uses("kafka") ? new KafkaContainer(KAFKA_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(KAFKA_ALIAS)
                .withListener(KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT)
                .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true") : null;
        this.localstack = uses("sqs") ? new LocalStackContainer(LOCALSTACK_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(SQS_ALIAS)
                .withServices(LocalStackContainer.Service.SQS) : null;
        this.rabbitmq = uses("amqp") ? new RabbitMQContainer(RABBIT_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(AMQP_ALIAS) : null;
        this.artemis = uses("jms") ? new GenericContainer<>(ARTEMIS_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(JMS_ALIAS)
                .withEnv("ARTEMIS_USER", "admin")
                .withEnv("ARTEMIS_PASSWORD", "admin")
                .withExposedPorts(61616)
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2))) : null;
        this.mosquitto = uses("mqtt") ? new GenericContainer<>(MOSQUITTO_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(MQTT_ALIAS)
                .withCopyToContainer(
                        Transferable.of("listener 1884\nallow_anonymous true\npersistence false\nlog_dest stdout\n"),
                        "/mosquitto/config/mosquitto.conf"
                )
                .withExposedPorts(1884)
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2))) : null;
    }

    static ProtocolTestEnvironment startFromSystemProperties() {
        String receive = System.getProperty("receive.protocol", "sqs");
        String send = System.getProperty("send.protocol", "kafka");
        ProtocolTestEnvironment environment = new ProtocolTestEnvironment(receive, send);
        environment.start();
        return environment;
    }

    String receiveProtocol() {
        return receiveProtocol;
    }

    String sendProtocol() {
        return sendProtocol;
    }

    String runId() {
        return runId;
    }

    String kafkaBootstrapServers() {
        return kafka != null ? kafka.getBootstrapServers() : "localhost:9092";
    }

    String kafkaHostPort() {
        return kafkaBootstrapServers();
    }

    String specKafkaHostPort() {
        return kafka != null ? KAFKA_ALIAS + ":" + KAFKA_INTERNAL_PORT : "localhost:9092";
    }

    String containerKafkaBootstrapServers() {
        return specKafkaHostPort();
    }

    String sqsEndpoint() {
        return localstack != null ? localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString() : "http://localhost:4566";
    }

    String sqsHost() {
        return sqsEndpoint() + "/000000000000";
    }

    String specSqsHost() {
        return localstack != null ? "http://" + SQS_ALIAS + ":" + SQS_INTERNAL_PORT + "/000000000000" : "http://localhost:4566/000000000000";
    }

    String containerSqsEndpoint() {
        return localstack != null ? "http://" + SQS_ALIAS + ":" + SQS_INTERNAL_PORT : "http://localhost:4566";
    }

    String mqttBrokerUrl() {
        return mosquitto != null ? "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1884) : "tcp://localhost:1884";
    }

    String specMqttBrokerUrl() {
        return mosquitto != null ? "tcp://" + MQTT_ALIAS + ":" + MQTT_INTERNAL_PORT : "tcp://localhost:1884";
    }

    String containerMqttBrokerUrl() {
        return specMqttBrokerUrl();
    }

    String mqttClientId() {
        return "order-api-client-" + runId;
    }

    String jmsHost() {
        return artemis != null ? artemis.getHost() : "localhost";
    }

    Integer jmsPort() {
        return artemis != null ? artemis.getMappedPort(61616) : 61616;
    }

    String jmsBrokerUrl() {
        return "tcp://" + jmsHost() + ":" + jmsPort();
    }

    String specJmsBrokerUrl() {
        return artemis != null ? "tcp://" + JMS_ALIAS + ":" + JMS_INTERNAL_PORT : "tcp://localhost:61616";
    }

    String containerJmsHost() {
        return artemis != null ? JMS_ALIAS : "localhost";
    }

    Integer containerJmsPort() {
        return artemis != null ? JMS_INTERNAL_PORT : 61616;
    }

    String rabbitHost() {
        return rabbitmq != null ? rabbitmq.getHost() : "localhost";
    }

    Integer rabbitPort() {
        return rabbitmq != null ? rabbitmq.getAmqpPort() : 5672;
    }

    String amqpBrokerUrl() {
        return "amqp://" + rabbitHost() + ":" + rabbitPort();
    }

    String specAmqpBrokerUrl() {
        return rabbitmq != null ? "amqp://" + AMQP_ALIAS + ":" + AMQP_INTERNAL_PORT : "amqp://localhost:5672";
    }

    String containerRabbitHost() {
        return rabbitmq != null ? AMQP_ALIAS : "localhost";
    }

    Integer containerRabbitPort() {
        return rabbitmq != null ? AMQP_INTERNAL_PORT : 5672;
    }

    Network network() {
        return network;
    }

    void stop() {
        stopQuietly(mosquitto);
        stopQuietly(artemis);
        stopQuietly(rabbitmq);
        stopQuietly(localstack);
        stopQuietly(kafka);
        closeQuietly(network);
    }

    private boolean uses(String protocol) {
        return protocol.equals(receiveProtocol) || protocol.equals(sendProtocol);
    }

    private void start() {
        startIfPresent(kafka);
        startIfPresent(localstack);
        startIfPresent(rabbitmq);
        startIfPresent(artemis);
        startIfPresent(mosquitto);

        if (localstack != null) {
            createSqsQueues();
        }

        if (kafka != null) {
            createKafkaTopics();
        }

        if (rabbitmq != null) {
            provisionRabbitTopology();
        }
    }

    private void createKafkaTopics() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(properties)) {
            adminClient.createTopics(QUEUES.stream().map(queue -> new NewTopic(queue, 1, (short) 1)).toList()).all().get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
    }

    private void createSqsQueues() {
        try (SqsClient sqsClient = SqsClient.builder()
                .region(Region.of(localstack.getRegion()))
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build()) {
            for (String queue : QUEUES) {
                sqsClient.createQueue(CreateQueueRequest.builder().queueName(queue).build());
            }
        }
    }

    private void provisionRabbitTopology() {
        try {
            String baseUrl = "http://" + rabbitmq.getHost() + ":" + rabbitmq.getHttpPort() + "/api";
            for (String queue : QUEUES) {
                put(baseUrl + "/queues/%2F/" + queue, "{\"durable\":true}");
                put(baseUrl + "/exchanges/%2F/" + queue, "{\"type\":\"direct\",\"durable\":true}");
                post(baseUrl + "/bindings/%2F/e/" + queue + "/q/" + queue, "{\"routing_key\":\"" + queue + "\"}");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to provision RabbitMQ topology", e);
        }
    }

    private void put(String endpoint, String body) throws IOException {
        send(endpoint, "PUT", body);
    }

    private void post(String endpoint, String body) throws IOException {
        send(endpoint, "POST", body);
    }

    private void send(String endpoint, String method, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod(method);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Basic Z3Vlc3Q6Z3Vlc3Q=");
        connection.getOutputStream().write(body.getBytes());
        int status = connection.getResponseCode();
        connection.disconnect();
        if (status >= 300) {
            throw new IOException("RabbitMQ management API call failed with status " + status + " for " + endpoint);
        }
    }

    private void startIfPresent(org.testcontainers.lifecycle.Startable container) {
        if (container != null) {
            container.start();
        }
    }

    private void stopQuietly(org.testcontainers.lifecycle.Startable container) {
        if (container != null) {
            try {
                container.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void closeQuietly(Network network) {
        if (network != null) {
            try {
                network.close();
            } catch (Exception ignored) {
            }
        }
    }
}
