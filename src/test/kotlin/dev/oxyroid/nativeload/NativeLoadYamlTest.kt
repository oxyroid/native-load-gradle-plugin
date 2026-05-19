package dev.oxyroid.nativeload

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeLoadYamlTest {
    @Test
    fun `parses valid config with defaults`() {
        val config = NativeLoadYaml.parse(writeConfig())

        assertEquals("demo", config.pack.id)
        assertEquals("native-packs", config.distribution.snapshotDirectory)
        assertEquals("release", config.distribution.runtimeVariant)
        assertEquals(listOf("core", "codec"), config.pack.loadOrder)
    }

    @Test
    fun `rejects unsupported schema version`() {
        val file = writeConfig(schemaVersion = 2)

        val error = assertFailsWith<IllegalArgumentException> {
            NativeLoadYaml.parse(file)
        }

        assertTrue(error.message.orEmpty().contains("Unsupported native-load.yml schemaVersion"))
    }

    @Test
    fun `rejects load order entries not declared as libraries`() {
        val file = writeConfig(loadOrder = "      - missing")

        val error = assertFailsWith<IllegalArgumentException> {
            NativeLoadYaml.parse(file)
        }

        assertTrue(error.message.orEmpty().contains("Undeclared: missing"))
    }

    private fun writeConfig(
        schemaVersion: Int = 1,
        loadOrder: String = ""
    ): File {
        val directory = createTempDir(prefix = "native-load-yaml-test")
        return File(directory, "native-load.yml").apply {
            writeText(
                """
                schemaVersion: $schemaVersion
                instrumentation:
                  packages:
                    - com.example
                  redirect:
                    owner: com/example/NativeLoadRuntime
                    method: loadLibrary
                distribution:
                  repository: app-repo
                  ref: main
                pack:
                  id: demo
                  artifacts:
                    - com.example:native-provider:1.0.0
                  libraries:
                    - core
                    - codec
                ${if (loadOrder.isBlank()) "" else "  loadOrder:\n$loadOrder"}
                """.trimIndent()
            )
        }
    }
}
