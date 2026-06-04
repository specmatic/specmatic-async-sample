package com.example.orderapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
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

    private AsyncContractTestSupport() {
    }

    static Path createSpecmaticWorkspace(ProtocolTestEnvironment environment, String appBaseUrl, boolean containerizedSpecmatic) throws IOException {
        Path workspace = BUILD_DIR.resolve("tmp/specmatic-workspaces/" + environment.receiveProtocol() + "-" + environment.sendProtocol() + "-" + environment.runId());
        Files.createDirectories(workspace.resolve("spec"));
        Files.createDirectories(workspace.resolve("examples"));

        copyDirectory(REPO_ROOT.resolve("examples"), workspace.resolve("examples"));
        Files.copy(REPO_ROOT.resolve("specmatic.yaml"), workspace.resolve("specmatic.yaml"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(REPO_ROOT.resolve("spec/spec.yaml"), workspace.resolve("spec/spec.yaml"), StandardCopyOption.REPLACE_EXISTING);

        updateProtocolsInSpec(workspace.resolve("spec/spec.yaml"), environment, containerizedSpecmatic);
        updateRunOptions(workspace.resolve("specmatic.yaml"), environment, containerizedSpecmatic);
        updateExampleBaseUrls(workspace.resolve("examples"), appBaseUrl);

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

    private static void updateProtocolsInSpec(Path specPath, ProtocolTestEnvironment environment, boolean containerizedSpecmatic) throws IOException {
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
            context.set(path, String.format("#/servers/%sServer", environment.receiveProtocol()));
        }

        for (String channel : SEND_CHANNELS) {
            String path = String.format("$.channels.%s.servers[0]['\\$ref']", channel);
            context.set(path, String.format("#/servers/%sServer", environment.sendProtocol()));
        }

        context.set("$.servers.kafkaServer.host", containerizedSpecmatic ? environment.specKafkaHostPort() : environment.kafkaHostPort());
        context.set("$.servers.sqsServer.host", containerizedSpecmatic ? environment.specSqsHost() : environment.sqsHost());
        context.set("$.servers.mqttServer.host", containerizedSpecmatic ? environment.specMqttBrokerUrl() : environment.mqttBrokerUrl());
        context.set("$.servers.jmsServer.host", containerizedSpecmatic ? environment.specJmsBrokerUrl() : environment.jmsBrokerUrl());
        context.set("$.servers.amqpServer.host", containerizedSpecmatic ? environment.specAmqpBrokerUrl() : environment.amqpBrokerUrl());

        mapper.writeValue(specPath.toFile(), context.json());
    }

    private static void updateRunOptions(Path configPath, ProtocolTestEnvironment environment, boolean containerizedSpecmatic) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new KotlinModule());
        JsonNode root = mapper.readTree(configPath.toFile());

        Configuration configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();

        DocumentContext context = JsonPath.using(configuration).parse(root);
        context.set("$.components.runOptions.orderAsyncServiceTest.asyncapi.servers[0].host", containerizedSpecmatic ? environment.specSqsHost() : environment.sqsHost());
        context.set("$.components.runOptions.orderAsyncServiceTest.asyncapi.servers[1].host", containerizedSpecmatic ? environment.specJmsBrokerUrl() : environment.jmsBrokerUrl());

        mapper.writeValue(configPath.toFile(), context.json());
    }

    private static void updateExampleBaseUrls(Path examplesDir, String appBaseUrl) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (Stream<Path> stream = Files.walk(examplesDir)) {
            for (Path path : stream.filter(candidate -> candidate.toString().endsWith(".json")).toList()) {
                JsonNode root = mapper.readTree(path.toFile());
                updateFixtureBaseUrls(root, "before", appBaseUrl);
                updateFixtureBaseUrls(root, "after", appBaseUrl);
                mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
            }
        }
    }

    private static void updateFixtureBaseUrls(JsonNode root, String sectionName, String appBaseUrl) {
        JsonNode section = root.get(sectionName);
        if (!(section instanceof ArrayNode fixtures)) {
            return;
        }

        for (JsonNode fixture : fixtures) {
            JsonNode request = fixture.get("http-request");
            if (request instanceof ObjectNode requestObject) {
                requestObject.put("baseUrl", appBaseUrl);
            }
        }
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
