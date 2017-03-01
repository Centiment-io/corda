package net.corda.demobench.model

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.*
import org.junit.Test

class NodeControllerTest {

    private val baseDir: Path = Paths.get(".").toAbsolutePath()
    private val controller = NodeController()

    @Test
    fun `test unique nodes after validate`() {
        val data = NodeData()
        data.legalName.value = "Node 1"
        assertNotNull(controller.validate(data))
        assertNull(controller.validate(data))
    }

    @Test
    fun `test unique key after validate`() {
        val data = NodeData()
        data.legalName.value = "Node 1"

        assertFalse(controller.keyExists("node1"))
        controller.validate(data)
        assertTrue(controller.keyExists("node1"))
    }

    @Test
    fun `test matching name after validate`() {
        val data = NodeData()
        data.legalName.value = "Node 1"

        assertFalse(controller.nameExists("Node 1"))
        assertFalse(controller.nameExists("Node1"))
        assertFalse(controller.nameExists("node 1"))
        controller.validate(data)
        assertTrue(controller.nameExists("Node 1"))
        assertTrue(controller.nameExists("Node1"))
        assertTrue(controller.nameExists("node 1"))
    }

    @Test
    fun `test first validated node becomes network map`() {
        val data = NodeData()
        data.legalName.value = "Node 1"
        data.artemisPort.value = 100000

        assertFalse(controller.hasNetworkMap())
        controller.validate(data)
        assertTrue(controller.hasNetworkMap())
    }

    @Test
    fun `test register unique nodes`() {
        val config = createConfig(legalName = "Node 2")
        assertTrue(controller.register(config))
        assertFalse(controller.register(config))
    }

    @Test
    fun `test unique key after register`() {
        val config = createConfig(legalName = "Node 2")

        assertFalse(controller.keyExists("node2"))
        controller.register(config)
        assertTrue(controller.keyExists("node2"))
    }

    @Test
    fun `test matching name after register`() {
        val config = createConfig(legalName = "Node 2")

        assertFalse(controller.nameExists("Node 2"))
        assertFalse(controller.nameExists("Node2"))
        assertFalse(controller.nameExists("node 2"))
        controller.register(config)
        assertTrue(controller.nameExists("Node 2"))
        assertTrue(controller.nameExists("Node2"))
        assertTrue(controller.nameExists("node 2"))
    }

    @Test
    fun `test register network map node`() {
        val config = createConfig(legalName = "Node is Network Map")
        assertTrue(config.isNetworkMap())

        assertFalse(controller.hasNetworkMap())
        controller.register(config)
        assertTrue(controller.hasNetworkMap())
    }

    @Test
    fun `test register non-network-map node`() {
        val config = createConfig(legalName = "Node is not Network Map")
        config.networkMap = NetworkMapConfig("Notary", 10000)
        assertFalse(config.isNetworkMap())

        assertFalse(controller.hasNetworkMap())
        controller.register(config)
        assertFalse(controller.hasNetworkMap())
    }

    @Test
    fun `test valid ports`() {
        assertFalse(controller.isPortValid(NodeController.minPort - 1))
        assertTrue(controller.isPortValid(NodeController.minPort))
        assertTrue(controller.isPortValid(NodeController.maxPort))
        assertFalse(controller.isPortValid(NodeController.maxPort + 1))
    }

    @Test
    fun `test artemis port is max`() {
        val config = createConfig(artemisPort = NodeController.firstPort + 1234)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(NodeController.firstPort + 1235, controller.nextPort)
    }

    @Test
    fun `test web port is max`() {
        val config = createConfig(webPort = NodeController.firstPort + 2356)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(NodeController.firstPort + 2357, controller.nextPort)
    }

    @Test
    fun `test H2 port is max`() {
        val config = createConfig(h2Port = NodeController.firstPort + 3478)
        assertEquals(NodeController.firstPort, controller.nextPort)
        controller.register(config)
        assertEquals(NodeController.firstPort + 3479, controller.nextPort)
    }

    @Test
    fun `dispose node`() {
        val config = createConfig(legalName = "MyName")
        controller.register(config)

        assertEquals(NodeState.STARTING, config.state)
        assertTrue(controller.keyExists("myname"))
        controller.dispose(config)
        assertEquals(NodeState.DEAD, config.state)
        assertTrue(controller.keyExists("myname"))
    }

    private fun createConfig(
            legalName: String = "Unknown",
            nearestCity: String = "Nowhere",
            artemisPort: Int = -1,
            webPort: Int = -1,
            h2Port: Int = -1,
            services: List<String> = listOf("extra.service"),
            users: List<User> = listOf(user("guest"))
    ) = NodeConfig(
            baseDir,
            legalName = legalName,
            nearestCity = nearestCity,
            artemisPort = artemisPort,
            webPort = webPort,
            h2Port = h2Port,
            extraServices = services,
            users = users
    )

}