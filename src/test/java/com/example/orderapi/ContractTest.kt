package com.example.orderapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest
import java.nio.file.Path
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledOnOs(OS.WINDOWS)
class ContractTest {
    private val network = Network.newNetwork()
    private val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:4.13"))
        .withNetwork(network)
        .withNetworkAliases("localstack-sqs")
        .withServices(LocalStackContainer.Service.SQS)

    private lateinit var appContainer: GenericContainer<*>

    @BeforeAll
    fun setup() {
        localstack.start()
        createQueue("new-orders")
        createQueue("wip-orders")

        appContainer = GenericContainer(DockerImageName.parse("eclipse-temurin:17-jre"))
            .withNetwork(network)
            .withNetworkAliases("order-api")
            .withFileSystemBind("build/libs/app.jar", "/app/app.jar", BindMode.READ_ONLY)
            .withEnv("SERVER_PORT", "9090")
            .withEnv("SERVER_ADDRESS", "0.0.0.0")
            .withEnv("RECEIVE_PROTOCOL", "sqs")
            .withEnv("SEND_PROTOCOL", "sqs")
            .withEnv("SPRING_CLOUD_AWS_ENDPOINT", "http://localstack-sqs:4566")
            .withEnv("SPRING_CLOUD_AWS_SQS_ENDPOINT", "http://localstack-sqs:4566")
            .withCommand("java", "-jar", "/app/app.jar")
            .withStartupTimeout(Duration.ofMinutes(3))
            .withLogConsumer { print(it.utf8String) }
            .waitingFor(Wait.forListeningPort())

        appContainer.start()
    }

    @AfterAll
    fun cleanup() {
        if (::appContainer.isInitialized) {
            appContainer.stop()
        }
        localstack.stop()
        network.close()
    }

    @Test
    fun runContractTest() {
        val specmatic = GenericContainer(DockerImageName.parse("specmatic/enterprise:latest"))
            .withCommand("test")
            .withNetwork(network)
            .withFileSystemBind(Path.of(".").toAbsolutePath().normalize().toString(), "/usr/src/app", BindMode.READ_WRITE)
            .withWorkingDirectory("/usr/src/app")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer { print(it.utf8String) }

        try {
            specmatic.start()
            waitForSpecmaticToFinish(specmatic)
            assertThat(specmatic.logs).contains("Result: PASSED").doesNotContain("Result: FAILED")
        } finally {
            specmatic.stop()
        }
    }

    private fun createQueue(name: String) {
        sqsClient().use { sqs ->
            sqs.createQueue(CreateQueueRequest.builder().queueName(name).build())
        }
    }

    private fun sqsClient(): SqsClient =
        SqsClient.builder()
            .region(Region.of(localstack.region))
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localstack.accessKey, localstack.secretKey)
                )
            )
            .build()

    private fun waitForSpecmaticToFinish(container: GenericContainer<*>) {
        repeat(300) {
            if (!container.isRunning) {
                return
            }
            Thread.sleep(1000)
        }
        error("Specmatic did not finish within 5 minutes")
    }
}
