package dev.oxyroid.nativeload

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class NativePackGenerationSpec(
    val packId: String,
    val artifactCoordinates: List<String>,
    val aarFiles: List<File>,
    val libraries: List<String>,
    val loadOrder: List<String>,
    val assetPrefix: String,
    val manifestPrefix: String,
    val outputDirectory: File,
    val outputPath: String,
    val stagingDirectory: File
)

internal object NativePackGenerator {
    fun generate(spec: NativePackGenerationSpec) {
        val aarFiles = spec.aarFiles
            .filter { file -> file.extension == "aar" }
            .sortedBy { file -> file.name }
        require(aarFiles.size == spec.artifactCoordinates.size) {
            "Expected AAR artifacts ${spec.artifactCoordinates.joinToString()}, " +
                "got ${aarFiles.joinToString { file -> file.name }}."
        }

        val outputRoot = spec.outputDirectory.apply {
            deleteRecursively()
            mkdirs()
        }
        val stagingRoot = spec.stagingDirectory.apply {
            deleteRecursively()
            mkdirs()
        }

        aarFiles.forEach { aarFile -> extractNativeLibraries(aarFile, stagingRoot) }

        val abiDirectories = stagingRoot.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { file -> file.name }
        require(abiDirectories.isNotEmpty()) {
            "No native libraries found in configured AAR artifacts: ${spec.artifactCoordinates.joinToString()}. " +
                "Only jni/<abi>/*.so entries are extracted."
        }

        val assets = abiDirectories.map { abiDirectory ->
            val assetName = "${spec.assetPrefix}-${spec.packId}-${abiDirectory.name}.zip"
            val zipFile = File(outputRoot, assetName)
            val libraryFiles = spec.libraries.map { library -> File(abiDirectory, "lib$library.so") }
            val missingLibraries = libraryFiles.filterNot { file -> file.isFile }.map { file -> file.name }
            require(missingLibraries.isEmpty()) {
                "Missing native libraries for ABI '${abiDirectory.name}': ${missingLibraries.joinToString()}. " +
                    "Configured libraries: ${spec.libraries.joinToString()}. " +
                    "Artifacts: ${spec.artifactCoordinates.joinToString()}."
            }
            createZip(libraryFiles, zipFile)
            NativePackAsset(
                abi = abiDirectory.name,
                path = "${spec.outputPath}/$assetName",
                fileName = assetName,
                size = zipFile.length(),
                md5 = md5(zipFile),
                libraries = libraryFiles
                    .sortedBy { file -> file.name }
                    .map { file -> NativePackLibrary(file.name, file.length(), md5(file)) }
            )
        }

        val manifestFile = File(outputRoot, "${spec.manifestPrefix}-${spec.packId}.json")
        manifestFile.writeText(
            NativePackManifestRenderer.render(
                packId = spec.packId,
                loadOrder = spec.loadOrder,
                artifacts = spec.artifactCoordinates.map { coordinate -> parseArtifactCoordinate(coordinate) },
                assets = assets
            )
        )
    }

    private fun extractNativeLibraries(aarFile: File, outputRoot: File) {
        ZipFile(aarFile).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.startsWith("jni/") && entry.name.endsWith(".so") }
                .forEach { entry ->
                    val parts = entry.name.split('/')
                    if (parts.size != 3) return@forEach
                    val output = File(outputRoot, "${parts[1]}/${parts[2]}")
                    output.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().use { outputStream -> input.copyTo(outputStream) }
                    }
                }
        }
    }

    private fun createZip(files: List<File>, output: File) {
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            files.sortedBy { file -> file.name }.forEach { file ->
                val entry = ZipEntry(file.name).apply { time = 0L }
                zip.putNextEntry(entry)
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun parseArtifactCoordinate(coordinate: String): NativePackArtifact {
        val parts = coordinate.split(':')
        require(parts.size >= 3) {
            "Expected artifact coordinate in 'group:name:version' form, got '$coordinate'."
        }
        return NativePackArtifact(
            group = parts[0],
            name = parts[1],
            version = parts[2].substringBefore('@')
        )
    }
}
