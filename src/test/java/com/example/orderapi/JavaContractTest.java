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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledOnOs(OS.WINDOWS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class JavaContractTest {
    private static final ProtocolTestEnvironment ENVIRONMENT = ProtocolTestEnvironment.startFromSystemProperties();

    @Value("${receive.protocol}")
    private String receiveProtocol;

    @Value("${send.protocol}")
    private String sendProtocol;

    @LocalServerPort
    private int appPort;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("receive.protocol", ENVIRONMENT::receiveProtocol);
        registry.add("send.protocol", ENVIRONMENT::sendProtocol);
        registry.add("mqtt.client-id", ENVIRONMENT::mqttClientId);

        if ("kafka".equals(ENVIRONMENT.receiveProtocol()) || "kafka".equals(ENVIRONMENT.sendProtocol())) {
            registry.add("spring.kafka.bootstrap-servers", ENVIRONMENT::kafkaBootstrapServers);
        }
        if ("sqs".equals(ENVIRONMENT.receiveProtocol()) || "sqs".equals(ENVIRONMENT.sendProtocol())) {
            registry.add("spring.cloud.aws.endpoint", ENVIRONMENT::sqsEndpoint);
            registry.add("spring.cloud.aws.sqs.endpoint", ENVIRONMENT::sqsEndpoint);
        }
        if ("mqtt".equals(ENVIRONMENT.receiveProtocol()) || "mqtt".equals(ENVIRONMENT.sendProtocol())) {
            registry.add("mqtt.broker-url", ENVIRONMENT::mqttBrokerUrl);
        }
        if ("jms".equals(ENVIRONMENT.receiveProtocol()) || "jms".equals(ENVIRONMENT.sendProtocol())) {
            registry.add("spring.artemis.host", ENVIRONMENT::jmsHost);
            registry.add("spring.artemis.port", ENVIRONMENT::jmsPort);
        }
        if ("amqp".equals(ENVIRONMENT.receiveProtocol()) || "amqp".equals(ENVIRONMENT.sendProtocol())) {
            registry.add("spring.rabbitmq.host", ENVIRONMENT::rabbitHost);
            registry.add("spring.rabbitmq.port", ENVIRONMENT::rabbitPort);
        }
    }

    @BeforeAll
    void setup() throws Exception {
        System.out.printf("Test setup: receive=%s, send=%s%n", receiveProtocol, sendProtocol);
    }

    @AfterAll
    void cleanup() {
        ENVIRONMENT.stop();
    }

    @Test
    void runContractTest() throws Exception {
        System.out.printf("Running contract test for: receive=%s, send=%s%n", receiveProtocol, sendProtocol);

        // Ensure reports directory exists (as earlier)
        Files.createDirectories(new File("./build/reports/specmatic").toPath());
        Path workspace = AsyncContractTestSupport.createSpecmaticWorkspace(ENVIRONMENT, "http://localhost:" + appPort, false);

        // Build Specmatic CLI args (absolute overlay path for safety)
        var args = List.of("test");

        // Add env vars only if you need (e.g., proxies or AWS overrides for LocalStack)
        var env = Map.<String, String>of();

        // 🔁 Use your executor instead of inline ProcessBuilder
        SpecmaticExecutor exec = new SpecmaticExecutor(args, env, workspace.toFile());

        try {
            exec.start(); // starts the process and streams logs
            exec.verifySuccessfulExecutionWithNoFailures(); // asserts zero failures and process exit code == 0
        } finally {
            exec.stop(); // graceful shutdown of the process and log threads
            AsyncContractTestSupport.syncReports(workspace);
            AsyncContractTestSupport.deleteWorkspace(workspace);
        }
    }
}
