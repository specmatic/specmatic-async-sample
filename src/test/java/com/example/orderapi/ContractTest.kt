package com.example.orderapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.name

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledOnOs(OS.WINDOWS)
class ContractTest {
    private val specmaticDockerImage =
        System.getProperty("specmaticDockerImage")
            ?: System.getenv("SPECMATIC_DOCKER_IMAGE")
            ?: "specmatic/enterprise:latest"

    private val appDockerImage = DockerImageName.parse("eclipse-temurin:17-jre")
    private val environment = ProtocolTestEnvironment.startFromSystemProperties()
    private lateinit var appContainer: GenericContainer<*>

    @BeforeAll
    fun setup() {
        println("Test setup: receive=${environment.receiveProtocol()}, send=${environment.sendProtocol()}")
        appContainer = GenericContainer(appDockerImage)
            .withNetwork(environment.network())
            .withNetworkAliases("order-api")
            .withFileSystemBind(
                applicationJar().toAbsolutePath().toString(),
                "/app/app.jar",
                BindMode.READ_ONLY
            )
            .withEnv("SERVER_PORT", "9090")
            .withEnv("SERVER_ADDRESS", "0.0.0.0")
            .withEnv("RECEIVE_PROTOCOL", environment.receiveProtocol())
            .withEnv("SEND_PROTOCOL", environment.sendProtocol())
            .withEnv("MQTT_CLIENT_ID", environment.mqttClientId())
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", environment.containerKafkaBootstrapServers())
            .withEnv("SPRING_CLOUD_AWS_ENDPOINT", environment.containerSqsEndpoint())
            .withEnv("SPRING_CLOUD_AWS_SQS_ENDPOINT", environment.containerSqsEndpoint())
            .withEnv("MQTT_BROKER_URL", environment.containerMqttBrokerUrl())
            .withEnv("SPRING_ARTEMIS_HOST", environment.containerJmsHost())
            .withEnv("SPRING_ARTEMIS_PORT", environment.containerJmsPort().toString())
            .withEnv("SPRING_RABBITMQ_HOST", environment.containerRabbitHost())
            .withEnv("SPRING_RABBITMQ_PORT", environment.containerRabbitPort().toString())
            .withCommand("java", "-jar", "/app/app.jar")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withLogConsumer { print(it.utf8String) }
            .waitingFor(Wait.forListeningPort())

        appContainer.start()
    }

    @AfterAll
    fun afterAll() {
        appContainer.stop()
        environment.stop()
    }

    @Test
    fun runContractTest() {
        println("Running contract test for: receive=${environment.receiveProtocol()}, send=${environment.sendProtocol()}")
        Files.createDirectories(Path.of("./build/reports/specmatic"))
        val workspace = AsyncContractTestSupport.createSpecmaticWorkspace(
            environment,
            "http://order-api:9090",
            true
        )
        assertThat(Files.readString(workspace.resolve("examples/acceptOrder.json"))).contains("http://order-api:9090")
        assertThat(Files.readString(workspace.resolve("examples/outForDeliveryOrder.json"))).contains("http://order-api:9090")

        val specmaticContainer = GenericContainer(DockerImageName.parse(specmaticDockerImage))
            .withCommand("test")
            .withNetwork(environment.network())
            .withFileSystemBind(
                workspace.toAbsolutePath().toString(),
                "/usr/src/app",
                BindMode.READ_WRITE
            )
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer { print(it.utf8String) }
            .waitingFor(
                Wait.forLogMessage(".*(Failed:|Success).*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3))
            )

        try {
            specmaticContainer.start()

            val logs = specmaticContainer.logs
            assertThat(logs)
                .withFailMessage("Specmatic tests failed. Check logs above for details.")
                .doesNotContain("Result: FAILED")
                .contains("Result: PASSED")
        } finally {
            specmaticContainer.stop()
            AsyncContractTestSupport.syncReports(workspace)
            AsyncContractTestSupport.deleteWorkspace(workspace)
        }
    }

    private fun applicationJar(): Path =
        Files.list(Path.of("build/libs")).use { paths ->
            paths
                .filter { path -> path.name.endsWith(".jar") && !path.name.endsWith("-plain.jar") }
                .findFirst()
                .orElseThrow { IllegalStateException("Executable boot jar not found in build/libs") }
        }
}
