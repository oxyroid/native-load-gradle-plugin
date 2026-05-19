package dev.oxyroid.nativeload

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import dev.oxyroid.nativeload.instrumentation.NativeLoadClassVisitorFactory
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project

class NativeLoadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("nativeLoad", NativeLoadExtension::class.java)
        extension.configFile.convention(project.rootProject.layout.projectDirectory.file("native-load.yml"))
        val configProvider = extension.configFile.map { file -> NativeLoadYaml.parse(file.asFile) }

        project.plugins.withId("com.android.library") {
            val android = project.extensions.getByType(LibraryExtension::class.java)
            android.buildFeatures.buildConfig = true

            val androidComponents = project.extensions.getByType(
                LibraryAndroidComponentsExtension::class.java
            )
            androidComponents.onVariants { variant ->
                val config = configProvider.get()
                if (config.distribution.runtimeConfigProject == null || config.distribution.runtimeConfigProject == project.path) {
                    val enabled = variant.buildType == config.distribution.runtimeVariant
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_ID",
                        BuildConfigField("String", config.pack.id.quoted(), null)
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_MANIFEST_PREFIX",
                        BuildConfigField("String", config.pack.manifestPrefix.quoted(), null)
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_ENABLED",
                        BuildConfigField("boolean", enabled.toString(), null)
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_REPOSITORY",
                        BuildConfigField(
                            "String",
                            if (enabled) config.distribution.repository.quoted() else "\"\"",
                            null
                        )
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_REF",
                        BuildConfigField(
                            "String",
                            if (enabled) config.distribution.ref.quoted() else "\"\"",
                            null
                        )
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_SNAPSHOT_PATH",
                        BuildConfigField(
                            "String",
                            if (enabled) config.snapshotPath().quoted() else "\"\"",
                            null
                        )
                    )
                }
            }
        }

        project.plugins.withId("com.android.application") {
            val androidComponents = project.extensions.getByType(
                ApplicationAndroidComponentsExtension::class.java
            )
            androidComponents.onVariants { variant ->
                val config = configProvider.get()
                if (variant.buildType != config.distribution.runtimeVariant) return@onVariants
                variant.packaging.jniLibs.excludes.addAll(
                    config.pack.libraries.map { library -> "**/lib$library.so" }
                )
                variant.instrumentation.transformClassesWith(
                    NativeLoadClassVisitorFactory::class.java,
                    InstrumentationScope.ALL
                ) { parameters ->
                    parameters.instrumentedPackages.set(config.instrumentation.packages)
                    parameters.redirectOwner.set(config.instrumentation.redirect.owner)
                    parameters.redirectMethod.set(config.instrumentation.redirect.method)
                }
                variant.instrumentation.setAsmFramesComputationMode(
                    FramesComputationMode.COPY_FRAMES
                )

                if (config.distribution.producerProject == null || config.distribution.producerProject == project.path) {
                    registerGenerateTask(project, variant.name, config)
                }
            }
        }
    }

    private fun registerGenerateTask(
        project: Project,
        variantName: String,
        config: NativeLoadConfig
    ) {
        val taskName = "generate${variantName.capitalized()}NativePacks"
        val artifactConfiguration = project.configurations.detachedConfiguration(
            *config.pack.artifacts.map { notation -> project.dependencies.create(notation) }.toTypedArray()
        ).apply {
            isTransitive = false
        }
        project.tasks.register(
            taskName,
            GenerateNativePacksTask::class.java,
            object : Action<GenerateNativePacksTask> {
                override fun execute(task: GenerateNativePacksTask) {
                    task.group = "native-load"
                    task.description = "Generates external native library packs for $variantName."
                    task.packId.set(config.pack.id)
                    task.artifactCoordinates.set(config.pack.artifacts)
                    task.aarFiles.from(artifactConfiguration)
                    task.libraries.set(config.pack.libraries)
                    task.loadOrder.set(config.pack.loadOrder)
                    task.assetPrefix.set(config.pack.assetPrefix)
                    task.manifestPrefix.set(config.pack.manifestPrefix)
                    task.outputPath.set(config.snapshotPath())
                    task.outputDirectory.set(project.rootProject.layout.projectDirectory.dir(config.snapshotPath()))
                    task.stagingDirectory.set(project.layout.buildDirectory.dir("tmp/nativeLoad/$taskName"))
                }
            }
        )
    }
}
