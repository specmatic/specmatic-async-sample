package com.example.orderapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.testcontainers.containers.ComposeContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class AsyncContractTestSupport {
    private static final Path REPO_ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Path BUILD_DIR = REPO_ROOT.resolve("build");
    private static final List<String> RECEIVE_CHANNELS = Arrays.asList(
            "NewOrderPlaced",
            "OrderCancellationRequested",
            "OrderDeliveryInitiated"
    );
    private static final List<String> SEND_CHANNELS = Arrays.asList(
            "OrderInitiated",
            "OrderCancelled",
            "OrderAccepted"
    );
    private static final List<String> QUEUES = Arrays.asList(
            "new-orders",
            "wip-orders",
            "to-be-cancelled-orders",
            "cancelled-orders",
            "accepted-orders",
            "out-for-delivery-orders"
    );

    private AsyncContractTestSupport() {
    }

    static ComposeContainer startInfrastructure() {
        ComposeContainer infrastructure = new ComposeContainer(new File("docker-compose.yml"));
        infrastructure.start();
        waitForInfrastructure();
        return infrastructure;
    }

    static Path createSpecmaticWorkspace(String receiveProtocol, String sendProtocol) throws IOException {
        Path workspace = BUILD_DIR.resolve("tmp/specmatic-workspaces/" + receiveProtocol + "-" + sendProtocol + "-" + UUID.randomUUID());
        Files.createDirectories(workspace.resolve("spec"));
        Files.createDirectories(workspace.resolve("examples"));

        copyDirectory(REPO_ROOT.resolve("examples"), workspace.resolve("examples"));
        Files.copy(REPO_ROOT.resolve("specmatic.yaml"), workspace.resolve("specmatic.yaml"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(REPO_ROOT.resolve("spec/spec.yaml"), workspace.resolve("spec/spec.yaml"), StandardCopyOption.REPLACE_EXISTING);

        updateProtocolsInSpec(workspace.resolve("spec/spec.yaml"), receiveProtocol, sendProtocol);
        return workspace;
    }

    static void syncReports(Path workspace) throws IOException {
        Path source = workspace.resolve("build/reports/specmatic");
        if (!Files.exists(source)) {
            return;
        }

        Path target = BUILD_DIR.resolve("reports/specmatic");
        deleteIfExists(target);
        copyDirectory(source, target);
    }

    static void deleteWorkspace(Path workspace) {
        try {
            deleteIfExists(workspace);
        } catch (IOException ignored) {
        }
    }

    private static void waitForInfrastructure() {
        waitFor("Kafka", Duration.ofMinutes(2), AsyncContractTestSupport::isKafkaReady);
        waitFor("LocalStack TCP", Duration.ofMinutes(2), () -> isTcpReady("localhost", 4566));
        waitFor("LocalStack SQS queues", Duration.ofMinutes(2), AsyncContractTestSupport::areSqsQueuesReady);
        waitFor("Mosquitto", Duration.ofMinutes(2), AsyncContractTestSupport::isMqttReady);
        waitFor("Artemis", Duration.ofMinutes(2), () -> isTcpReady("localhost", 61616));
        waitFor("RabbitMQ management", Duration.ofMinutes(2), AsyncContractTestSupport::isRabbitManagementReady);
        waitFor("RabbitMQ AMQP", Duration.ofMinutes(2), () -> isTcpReady("localhost", 5673));
    }

    private static boolean isKafkaReady() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");

        try (AdminClient adminClient = AdminClient.create(properties)) {
            adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean areSqsQueuesReady() {
        try (SqsClient sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()) {
            for (String queue : QUEUES) {
                sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queue).build());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMqttReady() {
        String clientId = "specmatic-readiness-" + UUID.randomUUID();
        try {
            MqttClient client = new MqttClient("tcp://localhost:1884", clientId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(5);
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            client.connect(options);
            client.disconnect();
            client.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isRabbitManagementReady() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:15672/api/overview").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Authorization", "Basic Z3Vlc3Q6Z3Vlc3Q=");
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            connection.disconnect();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTcpReady(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void waitFor(String label, Duration timeout, Supplier<Boolean> check) {
        Instant deadline = Instant.now().plus(timeout);
        Throwable lastError = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                if (Boolean.TRUE.equals(check.get())) {
                    return;
                }
            } catch (Throwable t) {
                lastError = t;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for " + label, e);
            }
        }

        throw new RuntimeException("Timed out waiting for " + label, lastError);
    }

    private static void updateProtocolsInSpec(Path specPath, String receiveProtocol, String sendProtocol) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new KotlinModule());
        JsonNode root = mapper.readTree(specPath.toFile());

        Configuration configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();

        DocumentContext context = JsonPath.using(configuration).parse(root);

        for (String channel : RECEIVE_CHANNELS) {
            String path = String.format("$.channels.%s.servers[0]['\\$ref']", channel);
            context.set(path, String.format("#/servers/%sServer", receiveProtocol));
        }

        for (String channel : SEND_CHANNELS) {
            String path = String.format("$.channels.%s.servers[0]['\\$ref']", channel);
            context.set(path, String.format("#/servers/%sServer", sendProtocol));
        }

        mapper.writeValue(specPath.toFile(), context.json());
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(path)) {
            for (Path candidate : stream.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }
}
