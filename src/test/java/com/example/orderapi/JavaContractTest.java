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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.ComposeContainer;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "receive.protocol=sqs",
                "send.protocol=kafka"
        }
)
public class JavaContractTest {

    private final String specPath = "./spec/spec.yaml";

    private static ComposeContainer infrastructure;

    static {
        startInfra();
    }

    private static void startInfra() {
        try {
            System.out.println("Starting infrastructure via docker-compose...");
            infrastructure = new ComposeContainer(new File("docker-compose.yml"));
            infrastructure.start();
            System.out.println("Infrastructure started via docker-compose");
            // retain original sleep (consider replacing with Wait strategies)
            Thread.sleep(20_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for infrastructure start", e);
        }
    }

    @Value("${receive.protocol}")
    private String receiveProtocol;

    @Value("${send.protocol}")
    private String sendProtocol;

    @BeforeAll
    void setup() throws Exception {
        System.out.printf("Test setup: receive=%s, send=%s%n", receiveProtocol, sendProtocol);
        updateProtocolsInSpec(receiveProtocol, sendProtocol);
    }

    @AfterAll
    void afterAll() {
        if (infrastructure != null) {
            infrastructure.stop();
        }
    }

    @Test
    void runContractTest() throws Exception {
        System.out.printf("Running contract test for: receive=%s, send=%s%n", receiveProtocol, sendProtocol);

        // Ensure reports directory exists (as earlier)
        Files.createDirectories(new File("./build/reports/specmatic").toPath());

        // Build Specmatic CLI args (absolute overlay path for safety)
        var args = List.of("test");

        // Add env vars only if you need (e.g., proxies or AWS overrides for LocalStack)
        var env = Map.<String, String>of();

        // 🔁 Use your executor instead of inline ProcessBuilder
        SpecmaticExecutor exec = new SpecmaticExecutor(args, env);

        try {
            exec.start(); // starts the process and streams logs
            exec.verifySuccessfulExecutionWithNoFailures(); // asserts zero failures and process exit code == 0
        } finally {
            exec.stop(); // graceful shutdown of the process and log threads
        }
    }

    private void updateProtocolsInSpec(String receiveProtocol, String sendProtocol) throws Exception {
        System.out.println("Modifying spec.yaml:");
        System.out.printf("  - Receive channels will use: %sServer%n", receiveProtocol);
        System.out.printf("  - Send channels will use: %sServer%n", sendProtocol);

        File file = new File(specPath);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new KotlinModule());
        JsonNode root = mapper.readTree(file);

        Configuration configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();

        DocumentContext context = JsonPath.using(configuration).parse(root);

        List<String> receiveChannels = Arrays.asList("NewOrderPlaced", "OrderCancellationRequested", "OrderDeliveryInitiated");
        List<String> sendChannels = Arrays.asList("OrderInitiated", "OrderCancelled", "OrderAccepted");

        // JSONPath: bracket-notation for "$ref" and escape $ in Java string literal
        for (String channel : receiveChannels) {
            String path = String.format("$.channels.%s.servers[0]['\\$ref']", channel);
            context.set(path, String.format("#/servers/%sServer", receiveProtocol));
        }
        for (String channel : sendChannels) {
            String path = String.format("$.channels.%s.servers[0]['\\$ref']", channel);
            context.set(path, String.format("#/servers/%sServer", sendProtocol));
        }

        // Write back YAML
        JsonNode updated = context.json();
        mapper.writeValue(file, updated);
    }
}
