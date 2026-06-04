package com.example.orderapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    // Note - Update these to try out different protocol combinations
    properties = [
        "receive.protocol=sqs",
        "send.protocol=kafka"
    ]
)
class ContractTest {
    private val specmaticDockerImage =
        System.getProperty("specmaticDockerImage")
            ?: System.getenv("SPECMATIC_DOCKER_IMAGE")
            ?: "specmatic/enterprise:latest"

    companion object {
        private lateinit var infrastructure: ComposeContainer

        init {
            startInfra()
        }

        private fun startInfra() {
            println("Starting infrastructure via docker-compose...")
            infrastructure = AsyncContractTestSupport.startInfrastructure()
            println("Infrastructure started via docker-compose")
        }
    }

    @Value($$"${receive.protocol}")
    private lateinit var receiveProtocol: String

    @Value($$"${send.protocol}")
    private lateinit var sendProtocol: String

    @BeforeAll
    fun setup() {
        println("Test setup: receive=$receiveProtocol, send=$sendProtocol")
    }

    @AfterAll
    fun afterAll() {
        infrastructure.stop()
    }

    @Test
    fun runContractTest() {
        println("Running contract test for: receive=$receiveProtocol, send=$sendProtocol")
        Files.createDirectories(Path.of("./build/reports/specmatic"))
        val workspace = AsyncContractTestSupport.createSpecmaticWorkspace(receiveProtocol, sendProtocol)

        val specmaticContainer = GenericContainer(DockerImageName.parse(specmaticDockerImage))
            .withCommand("test")
            .withFileSystemBind(
                workspace.toAbsolutePath().toString(),
                "/usr/src/app",
                BindMode.READ_WRITE
            )
            .withNetworkMode("host")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer { print(it.utf8String) }
            .waitingFor(Wait.forLogMessage(".*(Failed:|Success).*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))

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
}
