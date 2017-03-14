package net.corda.docs

import com.typesafe.config.Config
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FullNodeConfiguration
import net.corda.verifier.Verifier
import net.corda.verifier.VerifierConfiguration
import org.junit.Test
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.declaredMemberProperties

class ExampleConfigTest {

    private fun <A : Any> readAndCheckConfigurations(vararg configFilenames: String, loadConfig: (Path) -> A) {
        configFilenames.forEach {
            val configFileResource = ExampleConfigTest::class.java.classLoader.getResource(it)
            val config = loadConfig(Paths.get(configFileResource.toURI()))
            // Force the config fields as they are resolved lazily
            config.javaClass.kotlin.declaredMemberProperties.forEach { member ->
                member.get(config)
            }
        }
    }

    @Test
    fun `example node_confs parses fine`() {
        readAndCheckConfigurations(
                "example-node.conf",
                "example-out-of-process-verifier-node.conf",
                "example-network-map-node.conf"
        ) {
            val baseDirectory = Paths.get("some-example-base-dir")
            FullNodeConfiguration(
                    baseDirectory,
                    ConfigHelper.loadConfig(
                            baseDirectory = baseDirectory,
                            configFile = it
                    )
            )
        }
    }

    @Test
    fun `example verifier_conf parses fine`() {
        readAndCheckConfigurations(
                "example-verifier.conf"
        ) {
            val baseDirectory = Paths.get("some-example-base-dir")
            Verifier.loadConfiguration(baseDirectory, it)
        }
    }
}