package dev.oxyroid.nativeload

import java.io.File
import org.yaml.snakeyaml.Yaml

object NativeLoadYaml {
    fun parse(file: File): NativeLoadConfig {
        require(file.isFile) {
            "Native load config not found: ${file.absolutePath}. " +
                "Create native-load.yml at the root project or configure nativeLoad.configFile."
        }
        val document = yamlMap(Yaml().load(file.readText()), "root")
        val schemaVersion = document.int("schemaVersion", "schemaVersion")
        require(schemaVersion == 1) {
            "Unsupported native-load.yml schemaVersion: $schemaVersion. Supported schemaVersion is 1."
        }

        val instrumentation = document.map("instrumentation", "instrumentation")
        val redirect = instrumentation.map("redirect", "instrumentation.redirect")
        val distribution = document.map("distribution", "distribution")
        val pack = document.map("pack", "pack")
        val libraries = pack.stringList("libraries", "pack.libraries")
        val loadOrder = pack.optionalStringList("loadOrder", "pack.loadOrder") ?: libraries
        val undeclaredLoadOrderItems = loadOrder.filterNot { library -> library in libraries }
        require(undeclaredLoadOrderItems.isEmpty()) {
            "Every pack.loadOrder item must also be declared in pack.libraries. " +
                "Undeclared: ${undeclaredLoadOrderItems.joinToString()}."
        }
        return NativeLoadConfig(
            instrumentation = InstrumentationConfig(
                packages = instrumentation.stringList("packages", "instrumentation.packages"),
                redirect = RedirectConfig(
                    owner = redirect.string("owner", "instrumentation.redirect.owner"),
                    method = redirect.string("method", "instrumentation.redirect.method")
                )
            ),
            distribution = DistributionConfig(
                repository = distribution.string("repository", "distribution.repository"),
                ref = distribution.string("ref", "distribution.ref"),
                snapshotDirectory = distribution.optionalString(
                    "snapshotDirectory",
                    "distribution.snapshotDirectory"
                ) ?: "native-packs",
                runtimeVariant = distribution.optionalString("runtimeVariant", "distribution.runtimeVariant") ?: "release",
                producerProject = distribution.optionalString("producerProject", "distribution.producerProject"),
                runtimeConfigProject = distribution.optionalString("runtimeConfigProject", "distribution.runtimeConfigProject")
            ),
            pack = NativePackConfig(
                id = pack.string("id", "pack.id"),
                assetPrefix = pack.optionalString("assetPrefix", "pack.assetPrefix") ?: "native-pack",
                manifestPrefix = pack.optionalString("manifestPrefix", "pack.manifestPrefix") ?: "native-pack",
                artifacts = pack.stringList("artifacts", "pack.artifacts"),
                libraries = libraries,
                loadOrder = loadOrder
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun yamlMap(value: Any?, path: String): Map<String, Any?> {
        return value as? Map<String, Any?> ?: error("Expected YAML object at $path.")
    }

    private fun Map<String, Any?>.map(key: String, path: String): Map<String, Any?> {
        return yamlMap(this[key], path)
    }

    private fun Map<String, Any?>.string(key: String, path: String): String {
        return this[key] as? String ?: error("Expected string property '$path'.")
    }

    private fun Map<String, Any?>.optionalString(key: String, path: String): String? {
        val value = this[key] ?: return null
        return value as? String ?: error("Expected string property '$path'.")
    }

    private fun Map<String, Any?>.int(key: String, path: String): Int {
        return when (val value = this[key]) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Expected integer property '$path'.")
        }
    }

    private fun Map<String, Any?>.stringList(key: String, path: String): List<String> {
        return optionalStringList(key, path) ?: error("Expected string list property '$path'.")
    }

    private fun Map<String, Any?>.optionalStringList(key: String, path: String): List<String>? {
        val value = this[key] ?: return null
        val list = value as? List<*> ?: error("Expected string list property '$path'.")
        return list.mapIndexed { index, item ->
            item as? String ?: error("Expected string property '$path[$index]'.")
        }
    }
}
