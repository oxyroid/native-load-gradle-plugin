package dev.oxyroid.nativeload

internal data class NativePackArtifact(
    val group: String,
    val name: String,
    val version: String
)

internal data class NativePackAsset(
    val abi: String,
    val path: String,
    val fileName: String,
    val size: Long,
    val md5: String,
    val libraries: List<NativePackLibrary>
)

internal data class NativePackLibrary(
    val name: String,
    val size: Long,
    val md5: String
)
