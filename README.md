# native-load-gradle-plugin

A small Gradle plugin for Android projects that externalizes selected native libraries into deterministic native packs and redirects `System.loadLibrary` calls to a custom runtime loader.

The plugin id is:

```kotlin
id("dev.oxyroid.native-load")
```

## What It Does

- Reads native-load configuration from `native-load.yml` by default.
- For Android library modules, writes `BuildConfig` fields that describe the active native pack.
- For the configured Android application runtime variant, excludes selected `.so` files from APK packaging.
- Instruments configured package prefixes so `System.loadLibrary(String)` calls are redirected to the configured owner/method.
- Registers `generate<Variant>NativePacks` tasks that extract native libraries from configured AAR artifacts and write zip assets plus a JSON manifest into the configured snapshot directory.

## Configuration Entry Point

By default, the plugin reads `native-load.yml` from the root project. You can override that path with the Gradle extension:

```kotlin
nativeLoad {
    configFile.set(rootProject.layout.projectDirectory.file("native-load.yml"))
}
```

The schema currently supports:

- `instrumentation.packages`: Java/Kotlin package prefixes to instrument.
- `instrumentation.redirect.owner`: JVM internal class name that owns the redirect method, for example `com/example/NativeLoadRuntime`.
- `instrumentation.redirect.method`: redirect method name.
- `distribution.repository`: source repository name used by consumers.
- `distribution.ref`: source branch or ref used by consumers.
- `distribution.snapshotDirectory`: output directory, defaulting to `native-packs`.
- `distribution.runtimeVariant`: Android build type to enable, defaulting to `release`.
- `distribution.producerProject`: optional application project that generates packs.
- `distribution.runtimeConfigProject`: optional library project that receives runtime `BuildConfig` fields.
- `pack.id`: pack id.
- `pack.artifacts`: non-transitive AAR artifact coordinates to extract.
- `pack.libraries`: native library names without `lib` prefix or `.so` suffix.
- `pack.loadOrder`: optional load order, defaulting to `pack.libraries`.
- `pack.assetPrefix`: optional generated zip prefix, defaulting to `native-pack`.
- `pack.manifestPrefix`: optional generated manifest prefix, defaulting to `native-pack`.

## Runtime Contract

The plugin only provides build-time pack generation and bytecode redirection. The host application must provide a runtime loader matching the configured redirect owner and method.

A typical contract is:

```kotlin
object NativeLoadRuntime {
    @JvmStatic
    fun loadLibrary(name: String) {
        // Resolve/download/cache/extract the generated native pack, then load the library.
    }
}
```

Requirements:

- The redirect owner must be a JVM internal class name, such as `com/example/NativeLoadRuntime`.
- The redirect method must be static from bytecode's perspective.
- The redirect method signature must be `(String) -> Unit`, JVM descriptor `(Ljava/lang/String;)V`.
- The runtime loader is responsible for reading generated `BuildConfig` fields, locating the manifest, selecting the ABI, validating hashes, extracting libraries, and deciding whether fallback to APK-bundled loading is allowed.
- If fallback calls `System.loadLibrary`, ensure it cannot recursively call the redirected path unexpectedly.
- Pack versioning should be tied to the APK/repository/ref strategy used by the host application.

## Native Pack Manifest

Generated manifests use `schemaVersion: 1` and include:

- `packId`
- `loadOrder`
- resolved artifact coordinates from `pack.artifacts`
- ABI-specific asset metadata
- zip and library `size` / `md5`

Example shape:

```json
{
  "schemaVersion": 1,
  "packId": "demo",
  "loadOrder": ["core"],
  "artifacts": [
    { "group": "com.example", "name": "native-provider", "version": "1.0.0" }
  ],
  "assets": {
    "arm64-v8a": {
      "path": "native-packs/demo/native-pack-demo-arm64-v8a.zip",
      "fileName": "native-pack-demo-arm64-v8a.zip",
      "size": 1234,
      "md5": "...",
      "libraries": [
        { "name": "libcore.so", "size": 123, "md5": "..." }
      ]
    }
  }
}
```

Compatibility rules:

- Runtime loaders should fail fast on unknown `schemaVersion` values.
- New fields should be treated as optional unless a future schema version states otherwise.
- Existing schema version 1 fields should remain backward compatible.

## Artifact Resolution and Packaging Behavior

- `pack.artifacts` are resolved non-transitively. Every AAR that contains one of the target `.so` files must be listed explicitly.
- Native pack generation extracts only `jni/<abi>/*.so` entries from resolved AAR files.
- `pack.loadOrder` must only contain names declared in `pack.libraries`.
- APK `.so` exclusion is currently global by filename using patterns like `**/libcore.so`; another dependency with the same library file name will also be excluded.

## Limitations

- Current instrumentation only rewrites `System.loadLibrary(String)`.
- `System.load(String)`, `Runtime.getRuntime().loadLibrary(String)`, reflection, JNI-side loading, and third-party wrappers are not rewritten.
- The plugin does not include runtime download, cache, extraction, verification, fallback, rollout, rollback, or crash-attribution logic.
- Native pack generation only processes AAR `jni/<abi>/*.so` entries.
- AAR artifact resolution is non-transitive.
- APK `.so` exclusion is by global file name pattern.
- There is not yet a published stable Maven Central or Gradle Plugin Portal release process documented here.

## Common Commands

From this plugin directory:

```bash
gradle build
```

From a parent workspace that includes this repository as a build:

```bash
./gradlew :native-load-gradle-plugin:build
```

For a host Android application that applies the plugin, inspect and run the generated task:

```bash
./gradlew :app:smartphone:tasks --all
./gradlew :app:smartphone:generateReleaseNativePacks
```

## Notes

- This plugin is intentionally small and focused on native pack workflows; avoid adding host-app-specific behavior here unless it belongs to the generic native-load mechanism.
