package com.example.orderapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("mqtt-kafka")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
class ContractTest {
    private val overlayFilePath = "./src/test/resources/overlays/mqtt-kafka.yaml"

    companion object {
        private lateinit var infrastructure: ComposeContainer

        init {
            startInfra()
        }

        private fun startInfra() {
            println("Starting infrastructure via docker-compose...")
            infrastructure = ComposeContainer(File("docker-compose.yml"))
            infrastructure.start()
            println("Infrastructure started via docker-compose")
            Thread.sleep(20000)
        }
    }

    @AfterAll
    fun afterAll() {
        infrastructure.stop()
    }

    @Test
    fun runContractTest() {
        val specmaticContainer = GenericContainer(DockerImageName.parse("specmatic/specmatic-async"))
            .withCommand("test", "--overlay=overlay.yaml")
            .withFileSystemBind(
                "./specmatic.yaml",
                "/usr/src/app/specmatic.yaml",
                BindMode.READ_ONLY
            )
            .withFileSystemBind(
                "./spec",
                "/usr/src/app/spec",
                BindMode.READ_ONLY
            )
            .withFileSystemBind(
                overlayFilePath,
                "/usr/src/app/overlay.yaml",
                BindMode.READ_ONLY
            )
            .withFileSystemBind(
                "./build/reports/specmatic",
                "/usr/src/app/build/reports/specmatic",
                BindMode.READ_WRITE
            )
            .withNetworkMode("host")
            .withStartupTimeout(Duration.ofMinutes(5))
            .withLogConsumer { print(it.utf8String) }
            .waitingFor(Wait.forLogMessage(".*(Failed:|Success).*", 1)
                .withStartupTimeout(Duration.ofMinutes(3)))

        try {
            specmaticContainer.start()
            // Wait for tests to complete
            Thread.sleep(2000)

            val logs = specmaticContainer.logs
            assertThat(logs)
                .withFailMessage("Specmatic tests failed. Check logs above for details.")
                .doesNotContain("Passed: 0")
                .contains("Failed: 0")
        } finally {
            specmaticContainer.stop()
        }
    }
}