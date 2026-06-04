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
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.ComposeContainer;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "receive.protocol=sqs",
                "send.protocol=kafka"
        }
)
public class JavaContractTest {

    private static ComposeContainer infrastructure;

    static {
        startInfra();
    }

    private static void startInfra() {
        System.out.println("Starting infrastructure via docker-compose...");
        infrastructure = AsyncContractTestSupport.startInfrastructure();
        System.out.println("Infrastructure started via docker-compose");
    }

    @Value("${receive.protocol}")
    private String receiveProtocol;

    @Value("${send.protocol}")
    private String sendProtocol;

    @BeforeAll
    void setup() throws Exception {
        System.out.printf("Test setup: receive=%s, send=%s%n", receiveProtocol, sendProtocol);
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
        Path workspace = AsyncContractTestSupport.createSpecmaticWorkspace(receiveProtocol, sendProtocol);

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
