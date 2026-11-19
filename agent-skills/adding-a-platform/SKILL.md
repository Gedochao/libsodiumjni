---
name: libsodiumjni-adding-a-platform
description: Support a new OS / CPU architecture combination in libsodiumjni (build, runtime loading, CI, releases). Use when adding a platform, or changing classifiers or the native resource layout.
---

# Adding a platform (libsodiumjni)

Platforms are identified by a classifier `<arch>-<os>` (e.g. `aarch64-pc-linux`), the one Scala CLI computes for the
platform (`FetchExternalBinary.platformSuffix` in Scala CLI). Check Scala CLI's name for it before choosing one.

1. **Runtime**: map the platform in `LoadLibrary.resourcePath(osName, osArch)` to a `META-INF/native/<dir>/` path,
   with a test in `LoadLibraryTests` (written first). Unsupported platforms must keep failing with an
   `UnsupportedOperationException`, rather than falling back to another architecture's library.

2. **Build**:
   - `nativeResourceDirs` in `project/utils/package.mill`: classifier → resource directory (must match `LoadLibrary`)
   - classifier of the build machine: `osClassifier` / `windowsClassifier` in `project/settings/package.mill`,
     derived from `hostArch` (the `os.arch` of the JVM running Mill)
   - toolchain / libsodium for that platform in `build.mill` (libsodium source build on Linux / macOS, MSVC build
     archive directories on Windows)

3. **CI** (`.github/workflows/ci.yml`): add a runner to the test matrix, with a JDK of the platform's native
   architecture (an emulated JDK would build the native library for the wrong architecture), and download its
   artifacts in the `check-release-natives` and `release` jobs.

4. **Release check**: add a row to `platforms` in `.github/scripts/check-release-natives.sh` (classifier, resource
   directory, extensions, extended regular expression for the `file -b` output). The output format depends on the
   version of `file` (the CI's and yours can differ): add actual outputs for a binary of that architecture to
   `check-release-natives-test.sh`, which checks that each one only matches the pattern of its own platform.

5. **Docs**: platforms table in `README.md`.

`PlatformTableTests` checks that `LoadLibrary`, `nativeResourceDirs`, `check-release-natives.sh` and the README list
the same platforms, with the same directories and extensions: run it after updating all of them.

On the new platform's CI runner, `NativeLibraryArchitectureTests` checks that the bundled library matches the JVM
architecture, and the release check catches missing or mislabeled artifacts.

Scala CLI usually needs changes too, to fetch libsodium (if needed) and libsodiumjni for the new platform. Mention
them in the PR description.
