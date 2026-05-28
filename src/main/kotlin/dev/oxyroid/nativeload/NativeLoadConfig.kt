package dev.oxyroid.nativeload

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
