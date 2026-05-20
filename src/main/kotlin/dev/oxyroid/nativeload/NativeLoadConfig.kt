package dev.oxyroid.nativeload

import java.io.File
import org.yaml.snakeyaml.Yaml

data class NativeLoadConfig(
    val instrumentation: InstrumentationConfig,
    val distribution: DistributionConfig,
    val pack: NativePackConfig
)

data class DistributionConfig(
    val repository: String,
    val ref: String,
    val snapshotDirectory: String,
    val runtimeVariant: String,
    val producerProject: String?,
    val runtimeConfigProject: String?
)

data class InstrumentationConfig(
    val packages: List<String>,
    val redirect: RedirectConfig
)

data class RedirectConfig(
    val owner: String,
    val method: String
)

data class NativePackConfig(
    val id: String,
    val assetPrefix: String,
    val manifestPrefix: String,
    val artifacts: List<String>,
    val libraries: List<String>,
    val loadOrder: List<String>
)

object NativeLoadYaml {
    fun parse(file: File): NativeLoadConfig {
        require(file.isFile) { "Native load config not found: ${file.absolutePath}" }
        val document = yamlMap(Yaml().load(file.readText()), "root")
        val schemaVersion = document.int("schemaVersion")
        require(schemaVersion == 1) { "Unsupported native-load.yml schemaVersion: $schemaVersion" }

        val instrumentation = document.map("instrumentation")
        val redirect = instrumentation.map("redirect")
        val distribution = document.map("distribution")
        val pack = document.map("pack")
        val libraries = pack.stringList("libraries")
        val loadOrder = pack.optionalStringList("loadOrder") ?: libraries
        require(loadOrder.all { library -> library in libraries }) {
            "Every loadOrder item must also be declared in pack.libraries."
        }
        return NativeLoadConfig(
            instrumentation = InstrumentationConfig(
                packages = instrumentation.stringList("packages"),
                redirect = RedirectConfig(
                    owner = redirect.string("owner"),
                    method = redirect.string("method")
                )
            ),
            distribution = DistributionConfig(
                repository = distribution.string("repository"),
                ref = distribution.string("ref"),
                snapshotDirectory = distribution.optionalString("snapshotDirectory") ?: "native-packs",
                runtimeVariant = distribution.optionalString("runtimeVariant") ?: "release",
                producerProject = distribution.optionalString("producerProject"),
                runtimeConfigProject = distribution.optionalString("runtimeConfigProject")
            ),
            pack = NativePackConfig(
                id = pack.string("id"),
                assetPrefix = pack.optionalString("assetPrefix") ?: "native-pack",
                manifestPrefix = pack.optionalString("manifestPrefix") ?: "native-pack",
                artifacts = pack.stringList("artifacts"),
                libraries = libraries,
                loadOrder = loadOrder
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun yamlMap(value: Any?, label: String): Map<String, Any?> {
        return value as? Map<String, Any?> ?: error("Expected YAML object at $label.")
    }

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> {
        return yamlMap(this[key], key)
    }

    private fun Map<String, Any?>.string(key: String): String {
        return this[key] as? String ?: error("Expected string property '$key'.")
    }

    private fun Map<String, Any?>.optionalString(key: String): String? {
        return this[key] as? String
    }

    private fun Map<String, Any?>.int(key: String): Int {
        return when (val value = this[key]) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Expected integer property '$key'.")
        }
    }

    private fun Map<String, Any?>.stringList(key: String): List<String> {
        return optionalStringList(key) ?: error("Expected string list property '$key'.")
    }

    private fun Map<String, Any?>.optionalStringList(key: String): List<String>? {
        val value = this[key] ?: return null
        return (value as? List<*>)?.map { item ->
            item as? String ?: error("Expected every item in '$key' to be a string.")
        }
    }
}
