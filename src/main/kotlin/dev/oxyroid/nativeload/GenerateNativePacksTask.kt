package dev.oxyroid.nativeload

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateNativePacksTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val stagingDirectory: DirectoryProperty

    @get:Input
    abstract val outputPath: Property<String>

    @get:Input
    abstract val packId: Property<String>

    @get:Input
    abstract val artifactCoordinates: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val aarFiles: ConfigurableFileCollection

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
        NativePackGenerator.generate(
            NativePackGenerationSpec(
                packId = packId.get(),
                artifactCoordinates = artifactCoordinates.get(),
                aarFiles = aarFiles.files.toList(),
                libraries = libraries.get(),
                loadOrder = loadOrder.get(),
                assetPrefix = assetPrefix.get(),
                manifestPrefix = manifestPrefix.get(),
                outputDirectory = outputDirectory.get().asFile,
                outputPath = outputPath.get(),
                stagingDirectory = stagingDirectory.get().asFile
            )
        )
    }
}
