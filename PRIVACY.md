# Privacy Policy — MasterFabric Rust (Android)

_Last updated: 16 November 2026_

## Overview

MasterFabric Rust (application id `co.masterfabric.rust`) is a demo app that shows a native Jetpack Compose
interface running on a shared Rust core. This policy covers the app built from this repository.

## Information we collect

None. The app has no accounts, no analytics, no advertising, no crash reporting SDKs and no
network code. It does not collect, transmit or sell personal data.

## Local data

All data stays on the device:

- The project list, favorites and search state live in memory in the Rust core.
- Benchmark and app-performance results are kept in memory. For automation, the app also writes
  `perf.json` / `ffi.json` into its app-specific external files directory (`Android/data/co.masterfabric.rust/files`), which needs no permission; it never leaves the device.
- The benchmark screen reads non-personal device information (model, Android version, CPU
  core count, memory size, emulator or not) only to label the results on screen.

Deleting the app removes all of this data.

## Third-party services

None. The Rust and Kotlin dependencies (AndroidX, Jetpack Compose, JNA) are compiled into the app and make no network requests.

## Children

The app does not collect any data from anyone, including children.

## Changes

Changes to this policy are published in this file in the repository.

## Contact

Questions about this policy: open an issue at <https://github.com/masterfabric/masterfabric-rust-android/issues>.
The app is published by MasterFabric (https://masterfabric.co).
