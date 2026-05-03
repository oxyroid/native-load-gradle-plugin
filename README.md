# native-load-gradle-plugin

Gradle plugin for native library pack generation and runtime native-load redirection.

The plugin id is:

```kotlin
id("dev.oxyroid.native-load")
```

## What It Does

- Reads `native-load.yml` from the root project.
- For Android library modules, writes `BuildConfig` fields that describe the active native pack.
- For the configured Android application runtime variant, excludes selected `.so` files from APK packaging.
- Instruments configured package prefixes so native library loads are redirected to the configured owner/method.
- Registers `generate<Variant>NativePacks` tasks that extract native libraries from configured AAR artifacts and write zip assets plus a JSON manifest into the configured snapshot directory.

## Configuration

The host project must provide `native-load.yml` at the root. The schema currently supports:

- `instrumentation.packages`: Java/Kotlin package prefixes to instrument.
- `instrumentation.redirect.owner`: JVM internal class name that owns the redirect method.
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

## Common Commands

From the parent workspace root:

```bash
./gradlew :app:smartphone:generateReleaseNativePacks --no-configuration-cache
./gradlew :app:smartphone:tasks --all --no-configuration-cache
```

From this plugin directory:

```bash
../gradlew :native-load-gradle-plugin:build
```

## Notes

- `generate<Variant>NativePacks` resolves configured AAR artifacts non-transitively.
- Native pack generation should be run with `--no-configuration-cache` until the task avoids execution-time `Project` access.
- This plugin is intentionally small and focused on native pack workflows; avoid adding host-app-specific behavior here unless it belongs to the generic native-load mechanism.
