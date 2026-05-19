package dev.oxyroid.nativeload

internal object NativePackManifestRenderer {
    fun render(
        packId: String,
        loadOrder: List<String>,
        artifacts: List<NativePackArtifact>,
        assets: List<NativePackAsset>
    ): String {
        val artifactJson = artifacts.joinToString(",\n") { artifact ->
            """    { "group": "${artifact.group}", "name": "${artifact.name}", "version": "${artifact.version}" }"""
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
            appendLine("  \"loadOrder\": [${loadOrder.joinToString(", ") { library -> "\"$library\"" }}],")
            appendLine("  \"artifacts\": [")
            appendLine(artifactJson)
            appendLine("  ],")
            appendLine("  \"assets\": {")
            appendLine(assetJson)
            appendLine("  }")
            appendLine("}")
        }
    }
}
