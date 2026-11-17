# Contributing to MasterFabric Rust (Android)

Thanks for your interest! This repo is a native Jetpack Compose app on a shared Rust core
(UniFFI). Contributions of all kinds are welcome: bug reports, new benchmarks, UI polish,
performance work and documentation.

## Ways to contribute

- **Report bugs** and **request features** through the issue templates.
- **Add a benchmark**: a Rust kernel plus a Kotlin twin that produce the same checksum.
- **Improve the UI** (Jetpack Compose / Material 3, light mode first, no overlapping content).
- **Improve docs**: README flow, build steps, comments.
- **Security issues**: never open a public issue, see [SECURITY.md](SECURITY.md).

## Development setup

### Prerequisites

```bash
# Required
# JDK 17+ (21 recommended), Android SDK (platform 36, build-tools 36, NDK r28+), emulator
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or your SDK path
```

### Quick start

```bash
./scripts/build-android.sh        # Rust .so (arm64-v8a, x86_64) via cargo-ndk + UniFFI Kotlin bindings
(cd platforms/android && ./gradlew assembleBenchmark)
./scripts/run-emulator.sh         # boots the AVD if none is running, installs and launches
cargo test --release
```

Generated files (the UniFFI Kotlin package `co/masterfabric/rust/core/`, `jniLibs/`,
`local.properties`) are build output and gitignored.

### Project structure

| Path | What |
|---|---|
| `crates/app-core` | state, reducer (`dispatch(Action)` → partial `ViewUpdate`), view models, benchmark kernels |
| `crates/app-ffi` | UniFFI surface (`AppCore`, `StateListener`, `PlatformBenchRunner`) |
| `platforms/android/app` | Compose app (`bridge/`, `ui/`, `bench/`, `brand/`) |
| `scripts/` | build, emulator and measure scripts |

## Contribution workflow

### 1. Fork and branch

```bash
git checkout -b feature/your-feature-name
git checkout -b fix/issue-description
git checkout -b docs/what-you-document
```

### 2. Make changes

**Architecture rules**
- Business state lives in Rust. Compose only renders and sends `Action`s.
- Keep `ViewUpdate`s partial: mark only the dirty slices.
- Long work (benchmarks) runs off the main thread.

**Rust**
- `cargo fmt --all`, `cargo clippy --workspace --all-targets --release` with 0 warnings.
- No allocations in hot loops; avoid `unsafe`.

**Kotlin**
- Compose with `mutableStateOf` slices in `CoreBridge`; no business logic in composables.
- Kotlin has no unsigned primitives in hot loops: keep `u64` in `Long` and `u32` in `Int`, use
  `ushr`, `toULong()` comparisons and the helpers in `bench/Common.kt` (`umod`, `roundHalfAway`).

**Benchmarks**
- Same input generator (XorShift32), same algorithm and same checksum in both languages.
- Quantize floating-point values per item before summing so checksums do not depend on order
  (needed for rayon variants).
- Add the test to `screens/benchmark.rs`, the Kotlin twin to `bench/`, and check
  that `./scripts/measure.sh check 1` shows `ok` for every row.

**Commit message format**

```bash
# Format: type: description
feat: add LZ4 benchmark with Kotlin twin
fix: correct checksum in Lanczos resize
docs: update launch arguments table
perf: remove allocation from blur inner loop
test: add flow test for category run
refactor: split heavy.rs by category
```

### 3. Test

```bash
cargo test --release
(cd platforms/android && ./gradlew assembleDebug assembleBenchmark)
./scripts/measure.sh check 1      # on a running emulator/device: every checksum must be ok
```

### 4. Submit a pull request

Push your branch, open a PR against `main` and fill in the PR template (include emulator
screenshots for UI changes).

## Code review

- At least one maintainer review before merge.
- CI-equivalent checks above must pass.
- Benchmark changes must show before/after numbers and checksum parity.

## Community

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Legal

### License
Copyright (c) 2026 MasterFabric. The project is licensed under the GNU AGPL v3.0
([LICENSE](LICENSE)) with the attribution and trademark terms in [NOTICE](NOTICE).
By contributing, you agree that your contributions are licensed under the same terms.

### Contributor agreement
By submitting a pull request you confirm that you have the right to license your contribution,
that it is your original work (or properly attributed), and that you license it under the terms
above.

## Questions?

- 💬 [GitHub Issues](https://github.com/masterfabric/masterfabric-rust-android/issues) (use the Question template)
- 🔒 Security: [private vulnerability report](https://github.com/masterfabric/masterfabric-rust-android/security/advisories/new)
