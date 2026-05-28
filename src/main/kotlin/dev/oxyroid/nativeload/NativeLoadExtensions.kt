package dev.oxyroid.nativeload

internal fun String.capitalized(): String {
    return replaceFirstChar { char -> char.uppercaseChar() }
}

internal fun String.quoted(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

internal fun NativeLoadConfig.snapshotPath(): String {
    return "${distribution.snapshotDirectory}/${pack.id}"
}
