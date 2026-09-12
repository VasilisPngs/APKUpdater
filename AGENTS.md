# GUpdater Project Instructions

## Git & Repository Workflow
- Push direct changes to `main`; do not open Pull Requests.
- Every completed change set must be published as exactly one commit on `main`.

## Product
- This repository contains the Android app GUpdater.
- GUpdater checks installed applications for Google LLC updates available from the APKMirror Google Inc. publisher feed.
- System and user applications are included by default.
- Disabled applications are excluded by default and may be included from Settings.
- Do not restore general-purpose application update discovery or non-Google update sources.

## Releases
- The product identity is `GUpdater`.
- Do not add a numeric product version to the visible product identity, release name, APK filename, release tag, or API User-Agent unless explicitly requested.
- GitHub Actions builds and publishes the GUpdater release from `main`.

## General rules
- Do the work, build, test, lint and verify when the environment allows it.
- Do not undo settled decisions or weaken requirements without a real technical reason.
- Prefer root-cause fixes and the simplest architecture that fully satisfies the requirements.
- Remove dead code, unused dependencies, resources, permissions and configuration.
- Avoid unnecessary CPU, RAM, GPU, battery, storage, network use, allocations, I/O, polling and redundant state.
- Keep code clean, direct, idiomatic and maintainable.
- All code and code comments are written in English.
- Do not add explanatory or redundant comments inside code.
- Use the latest appropriate official technologies, APIs, libraries and tools. Preview, beta, release candidate, alpha and canary are allowed when they are the newest appropriate choice.
- Verify current versions from official sources rather than relying on outdated examples.

## Android
- Use the latest appropriate official Kotlin, Jetpack Compose, Material 3, AndroidX, Android SDK, Android Gradle Plugin, Gradle and supported JDK.
- Use official Android and Material 3 components and native interaction patterns.
- Use platform defaults and dynamic color where appropriate; do not create custom application-wide palettes or themes.
- Query actual device capabilities instead of assuming manufacturer-specific behavior.
- Keep expensive work away from the UI thread.
- Prefer current Android APIs and remove obsolete compatibility layers and workarounds.

## Verification
- Before considering a change complete, verify build/lint results, dependencies, resources, release configuration and relevant runtime behavior.
