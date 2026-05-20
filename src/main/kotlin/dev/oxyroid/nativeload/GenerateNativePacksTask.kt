package dev.oxyroid.nativeload

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateNativePacksTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packId: Property<String>

    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:Input
    abstract val libraries: ListProperty<String>

    @get:Input
    abstract val loadOrder: ListProperty<String>

    @get:Input
    abstract val assetPrefix: Property<String>

    @get:Input
    abstract val manifestPrefix: Property<String>

    @TaskAction
    fun generate() {
        val configuration = project.configurations.detachedConfiguration(
            *artifacts.get().map { notation ->
                project.dependencies.create(notation)
            }.toTypedArray()
        ).apply {
            isTransitive = false
        }
        val artifacts = configuration.resolvedConfiguration.resolvedArtifacts
            .filter { artifact -> artifact.file.extension == "aar" }
            .sortedBy { artifact -> artifact.name }

        require(artifacts.size == this.artifacts.get().size) {
            "Expected AAR artifacts ${this.artifacts.get().joinToString()}, got ${artifacts.joinToString { it.name }}."
        }

        val packId = packId.get()
        val outputRoot = outputDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val outputPath = outputRoot.relativeTo(project.rootProject.projectDir).invariantSeparatorsPath
        val stagingRoot = File(temporaryDir, packId).apply {
            deleteRecursively()
            mkdirs()
        }

        artifacts.forEach { artifact -> extractNativeLibraries(artifact.file, stagingRoot) }

        val abiDirectories = stagingRoot.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
        require(abiDirectories.isNotEmpty()) { "No native libraries found in configured AAR artifacts." }

        val configuredLibraries = libraries.get()
        val assets = abiDirectories.map { abiDirectory ->
            val assetName = "${assetPrefix.get()}-$packId-${abiDirectory.name}.zip"
            val zipFile = File(outputRoot, assetName)
            val libraryFiles = configuredLibraries.map { library -> File(abiDirectory, "lib$library.so") }
            val missingLibraries = libraryFiles.filterNot { file -> file.isFile }.map { file -> file.name }
            require(missingLibraries.isEmpty()) {
                "Missing native libraries for ${abiDirectory.name}: ${missingLibraries.joinToString()}"
            }
            createZip(libraryFiles, zipFile)
            NativePackAsset(
                abi = abiDirectory.name,
                path = "$outputPath/$assetName",
                fileName = assetName,
                size = zipFile.length(),
                md5 = md5(zipFile),
                libraries = libraryFiles
                    .sortedBy { file -> file.name }
                    .map { file -> NativePackLibrary(file.name, file.length(), md5(file)) }
            )
        }

        val manifestFile = File(outputRoot, "${manifestPrefix.get()}-$packId.json")
        manifestFile.writeText(renderManifest(packId, artifacts, assets))
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
                    zip.getInputStream(entry).use { input -> output.outputStream().use { outputStream -> input.copyTo(outputStream) } }
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

    private fun renderManifest(
        packId: String,
        artifacts: List<ResolvedArtifact>,
        assets: List<NativePackAsset>
    ): String {
        val artifactJson = artifacts.joinToString(",\n") { artifact ->
            val id = artifact.moduleVersion.id
            """    { "group": "${id.group}", "name": "${artifact.name}", "version": "${id.version}" }"""
        }
        val assetJson = assets.joinToString(",\n") { asset ->
            val libraryJson = asset.libraries.joinToString(",\n") { library ->
                """        { "name": "${library.name}", "size": ${library.size}, "md5": "${library.md5}" }"""
            }
            """    "${asset.abi}": {
      "path": "${asset.path}",
      "fileName": "${asset.fileName}",
      "size": ${asset.size},
      "md5": "${asset.md5}",
      "libraries": [
$libraryJson
      ]
    }"""
        }
        return buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"packId\": \"$packId\",")
            appendLine("  \"loadOrder\": [${loadOrder.get().joinToString(", ") { library -> "\"$library\"" }}],")
            appendLine("  \"artifacts\": [")
            appendLine(artifactJson)
            appendLine("  ],")
            appendLine("  \"assets\": {")
            appendLine(assetJson)
            appendLine("  }")
            appendLine("}")
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

    private data class NativePackAsset(
        val abi: String,
        val path: String,
        val fileName: String,
        val size: Long,
        val md5: String,
        val libraries: List<NativePackLibrary>
    )

    private data class NativePackLibrary(
        val name: String,
        val size: Long,
        val md5: String
    )
}
