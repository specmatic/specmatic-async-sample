package com.example.orderapi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = [
        "receive.protocol=jms",
        "send.protocol=mqtt"
    ]
)
class ContractTest {
    private val generatedOverlayPath = "./build/generated-overlay.yaml"

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

    @Value("\${receive.protocol}")
    private lateinit var receiveProtocol: String

    @Value("\${send.protocol}")
    private lateinit var sendProtocol: String

    @Test
    fun runContractTest() {
        println("Running contract test for: receive=$receiveProtocol, send=$sendProtocol")

        // Generate overlay file based on protocols
        generateOverlayFile(receiveProtocol, sendProtocol)

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
                generatedOverlayPath,
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

    private fun generateOverlayFile(receiveProtocol: String, sendProtocol: String) {
        val receiveServer = "${receiveProtocol}Server"
        val sendServer = "${sendProtocol}Server"

        val overlayContent = """
overlay: 1.0.0
actions:
  # Channels for receiving messages (NewOrderPlaced, OrderCancellationRequested, OrderDeliveryInitiated)
  - target: ${'$'}.channels.NewOrderPlaced
    update:
      servers:
        - ${'$'}ref: '#/servers/$receiveServer'

  - target: ${'$'}.channels.OrderCancellationRequested
    update:
      servers:
        - ${'$'}ref: '#/servers/$receiveServer'

  - target: ${'$'}.channels.OrderDeliveryInitiated
    update:
      servers:
        - ${'$'}ref: '#/servers/$receiveServer'

  # Channels for sending messages (OrderInitiated, OrderCancelled, OrderAccepted)
  - target: ${'$'}.channels.OrderInitiated
    update:
      servers:
        - ${'$'}ref: '#/servers/$sendServer'

  - target: ${'$'}.channels.OrderCancelled
    update:
      servers:
        - ${'$'}ref: '#/servers/$sendServer'

  - target: ${'$'}.channels.OrderAccepted
    update:
      servers:
        - ${'$'}ref: '#/servers/$sendServer'

  # HTTP trigger for orderAccepted operation
  - target: ${'$'}.operations.orderAccepted
    update:
      x-specmatic-trigger:
        type: http
        method: PUT
        url: http://localhost:9000/orders
        expectedStatus: 200
        timeoutInSeconds: 5
        headers:
          Content-Type: application/json
        requestBody: '{"id":123,"status":"ACCEPTED","timestamp":"2025-04-12T14:30:00Z"}'

  # HTTP side-effect for initiateOrderDelivery operation
  - target: ${'$'}.operations.initiateOrderDelivery
    update:
      x-specmatic-side-effect:
        type: http
        method: GET
        url: http://localhost:9000/orders/123?status=SHIPPED
        expectedStatus: 200
        timeoutInSeconds: 5
""".trimIndent()

        File(generatedOverlayPath).writeText(overlayContent)
        println("Generated overlay file at: $generatedOverlayPath")
        println("Overlay configured for: receive=$receiveProtocol, send=$sendProtocol")
    }
}