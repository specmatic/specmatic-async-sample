package com.example.orderapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    // Note - Update these to try out different protocol combinations
    properties = [
        "receive.protocol=amqp",
        "send.protocol=kafka"
    ]
)
class ContractTest {
    private val specPath = "./spec/spec.yaml"
    private val overlayFilePath = "./src/test/resources/spec_overlay.yaml"

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

    @Value($$"${receive.protocol}")
    private lateinit var receiveProtocol: String

    @Value($$"${send.protocol}")
    private lateinit var sendProtocol: String

    @BeforeAll
    fun setup() {
        println("Test setup: receive=$receiveProtocol, send=$sendProtocol")
        updateProtocolsInSpec(receiveProtocol, sendProtocol)
    }

    @AfterAll
    fun afterAll() {
        infrastructure.stop()
    }

    @Test
    fun runContractTest() {
        println("Running contract test for: receive=$receiveProtocol, send=$sendProtocol")

        val specmaticContainer = GenericContainer(DockerImageName.parse("specmatic/specmatic-async"))
            .withCommand("test --overlay=overlay.yaml")
            .withImagePullPolicy(PullPolicy.alwaysPull())
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

    private fun updateProtocolsInSpec(receiveProtocol: String, sendProtocol: String) {
        println("Modifying spec.yaml:")
        println("  - Receive channels will use: ${receiveProtocol}Server")
        println("  - Send channels will use: ${sendProtocol}Server")

        val file = File(specPath)
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val root = mapper.readTree(file)

        val configuration = Configuration.builder()
            .jsonProvider(com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider())
            .mappingProvider(com.jayway.jsonpath.spi.mapper.JacksonMappingProvider())
            .build()

        val context = JsonPath.using(configuration).parse(root)

        val receiveChannels = listOf("NewOrderPlaced", "OrderCancellationRequested", "OrderDeliveryInitiated")
        val sendChannels = listOf("OrderInitiated", "OrderCancelled", "OrderAccepted")

        receiveChannels.forEach { channel ->
            context.set("$.channels.$channel.servers[0].\$ref", "#/servers/${receiveProtocol}Server")
        }
        sendChannels.forEach { channel ->
            context.set("$.channels.$channel.servers[0].\$ref", "#/servers/${sendProtocol}Server")
        }

        mapper.writeValue(file, root)
    }
}