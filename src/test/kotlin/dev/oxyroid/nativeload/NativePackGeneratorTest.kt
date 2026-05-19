package dev.oxyroid.nativeload

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativePackGeneratorTest {
    @Test
    fun `generates deterministic zip and manifest`() {
        val directory = createTempDir(prefix = "native-pack-generator-test")
        val aar = File(directory, "native-provider.aar")
        createAar(
            aar,
            mapOf(
                "jni/arm64-v8a/libcore.so" to "core-arm64",
                "jni/arm64-v8a/libcodec.so" to "codec-arm64"
            )
        )
        val spec = spec(directory, aar)

        NativePackGenerator.generate(spec)
        val firstManifest = File(spec.outputDirectory, "native-pack-demo.json").readText()
        val firstZipBytes = File(spec.outputDirectory, "native-pack-demo-arm64-v8a.zip").readBytes()
        NativePackGenerator.generate(spec)
        val secondManifest = File(spec.outputDirectory, "native-pack-demo.json").readText()
        val secondZipBytes = File(spec.outputDirectory, "native-pack-demo-arm64-v8a.zip").readBytes()

        assertEquals(firstManifest, secondManifest)
        assertTrue(firstZipBytes.contentEquals(secondZipBytes))
        assertTrue(firstManifest.contains("\"schemaVersion\": 1"))
        assertTrue(firstManifest.contains("\"loadOrder\": [\"core\", \"codec\"]"))
        ZipFile(File(spec.outputDirectory, "native-pack-demo-arm64-v8a.zip")).use { zip ->
            assertEquals(listOf("libcodec.so", "libcore.so"), zip.entries().asSequence().map { entry -> entry.name }.toList())
        }
    }

    @Test
    fun `fails when configured library is missing`() {
        val directory = createTempDir(prefix = "native-pack-generator-test")
        val aar = File(directory, "native-provider.aar")
        createAar(aar, mapOf("jni/arm64-v8a/libcore.so" to "core-arm64"))
        val spec = spec(directory, aar)

        val error = assertFailsWith<IllegalArgumentException> {
            NativePackGenerator.generate(spec)
        }

        assertTrue(error.message.orEmpty().contains("Missing native libraries for ABI 'arm64-v8a'"))
        assertTrue(error.message.orEmpty().contains("libcodec.so"))
    }

    private fun spec(directory: File, aar: File): NativePackGenerationSpec {
        return NativePackGenerationSpec(
            packId = "demo",
            artifactCoordinates = listOf("com.example:native-provider:1.0.0"),
            aarFiles = listOf(aar),
            libraries = listOf("core", "codec"),
            loadOrder = listOf("core", "codec"),
            assetPrefix = "native-pack",
            manifestPrefix = "native-pack",
            outputDirectory = File(directory, "native-packs/demo"),
            outputPath = "native-packs/demo",
            stagingDirectory = File(directory, "staging")
        )
    }

    private fun createAar(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.toSortedMap().forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
