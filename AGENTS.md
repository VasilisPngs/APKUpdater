# Android Project Instructions

## Git & repository workflow
- Push changes directly to `main`; do not open pull requests.
- Publish every completed change set as exactly one commit.

## General rules
- Do the work end to end.
- Do not undo settled decisions or weaken requirements without a real technical reason.
- Prefer root-cause fixes and the simplest architecture that fully satisfies the requirements.
- Remove dead code, unused dependencies, resources, permissions and configuration.
- Avoid unnecessary CPU, RAM, GPU, battery, storage, network use, allocations, I/O, polling and redundant state.
- Keep code clean, direct, idiomatic and maintainable.
- All code is written in English.
- Do not add explanatory or redundant comments inside code.
- Use the latest appropriate official technologies, APIs, libraries and tools, tracking the newest published build in any release channel, including preview, alpha, beta, release candidate and canary.
- A newer toolchain is only acceptable while the signed release APK still installs on a release device; never compile against a preview Android platform, which stamps the package as preview built and makes it unparseable on release builds.
- Verify current versions from official sources rather than relying on outdated examples.
- Do not add a numeric product version to the visible product identity, release name, APK filename, release tag, or API User-Agent unless explicitly requested.

## Android
- Use the latest appropriate official Kotlin, Jetpack Compose, AndroidX, Android SDK, Android Gradle Plugin, Gradle and supported JDK.
- The product design standard is the shared design system used by the owner's other projects (Adblock, GymTracker): the same colour tokens, type scale, corner radii, spacing scale, motion curves, component shapes and interaction states, ported to Compose.
- Build the interface from Compose foundation primitives so it matches that system exactly, rather than inheriting the visual defaults of Material 3; Material 3 remains the plumbing, not the appearance.
- Keep every value in the shared token set and apply it consistently across all screens and components; do not use dynamic color and do not introduce one-off colours, sizes, radii or durations.
- Obtain values from the relevant official platform API whenever the system or the device can provide them at runtime, including device capabilities, application metadata such as icons, labels and package information, locales and configuration, instead of assuming manufacturer-specific behavior or duplicating them in resources and hardcoded mappings.
- Keep expensive work away from the UI thread.
- Prefer current Android APIs and remove obsolete compatibility layers and workarounds.
- Draw edge to edge and let the window insets place the content, rather than reserving space manually.
- Build visual effects with the official platform and Jetpack APIs only; never with a third party effect library.

## Verification
- Before considering a change complete, verify build/lint results, dependencies, resources, release configuration and relevant runtime behavior.

## Official references

### Design and UI
- Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Get started with Jetpack Compose: https://developer.android.com/develop/ui/compose/documentation
- App bars: https://developer.android.com/develop/ui/compose/components/app-bars
- State and Jetpack Compose: https://developer.android.com/develop/ui/compose/state
- Side effects in Compose: https://developer.android.com/develop/ui/compose/side-effects
- Lazy lists and grids: https://developer.android.com/develop/ui/compose/lists
- Animations in Compose: https://developer.android.com/develop/ui/compose/animation/quick-guide
- Adaptive apps: https://developer.android.com/develop/ui/compose/layouts/adaptive/get-started-with-adaptive-apps
- Support different display sizes: https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes
- Dynamic color: https://developer.android.com/develop/ui/views/theming/dynamic-colors
- Dark theme: https://developer.android.com/develop/ui/views/theming/darktheme

### Graphics and system surfaces
- Graphics in Compose: https://developer.android.com/develop/ui/compose/graphics/draw/overview
- Graphics modifiers: https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- Window insets: https://developer.android.com/develop/ui/compose/system/insets
- System bars: https://developer.android.com/develop/ui/compose/system/system-bars
- Edge to edge: https://developer.android.com/develop/ui/views/layout/edge-to-edge

### Platform behavior
- Permissions: https://developer.android.com/guide/topics/permissions/overview
- Package visibility: https://developer.android.com/training/package-visibility
- Background tasks: https://developer.android.com/develop/background-work/background-tasks
- Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
- Predictive back gesture: https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
- Kotlin coroutines on Android: https://developer.android.com/kotlin/coroutines

### Architecture, quality and testing
- Guide to app architecture: https://developer.android.com/topic/architecture
- Core app quality guidelines: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview
- Test apps on Android: https://developer.android.com/training/testing

### Build and release
- Configure your build: https://developer.android.com/build
- Version catalogs: https://developer.android.com/build/migrate-to-catalogs
- App optimization with R8: https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- Sign your app: https://developer.android.com/studio/publish/app-signing
- Platform and API versions: https://developer.android.com/tools/releases/platforms
- Latest Android developer updates: https://developer.android.com/latest-updates
- AndroidX releases: https://developer.android.com/jetpack/androidx/versions
- Compose releases: https://developer.android.com/jetpack/androidx/releases/compose
- Compose UI release notes: https://developer.android.com/jetpack/androidx/releases/compose-ui
- Android Gradle Plugin release notes: https://developer.android.com/build/releases/gradle-plugin
