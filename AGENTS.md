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
- Use the latest appropriate official technologies, APIs, libraries and tools, and only their stable releases. Never depend on a preview, alpha, beta, release candidate or canary version.
- Verify current versions from official sources rather than relying on outdated examples.
- Do not add a numeric product version to the visible product identity, release name, APK filename, release tag, or API User-Agent unless explicitly requested.

## Android
- Use the latest appropriate official Kotlin, Jetpack Compose, Material 3, AndroidX, Android SDK, Android Gradle Plugin, Gradle and supported JDK.
- Treat native Android/Google UI and UX as the product design standard across the entire application, using the official Material 3 components, layouts, typography, interaction patterns, navigation, loading and progress states, dialogs and controls wherever the platform provides a solution.
- Never hand build an imitation of a native component, visual or behavior.
- Use platform defaults and dynamic color where appropriate; do not create custom application-wide palettes or themes.
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
- Animations in Compose: https://developer.android.com/develop/ui/compose/animation/introduction
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
- Android releases: https://developer.android.com/about/versions
- Latest Android developer updates: https://developer.android.com/latest-updates
- AndroidX releases: https://developer.android.com/jetpack/androidx/versions
- Compose releases: https://developer.android.com/jetpack/androidx/releases/compose
- Compose UI release notes: https://developer.android.com/jetpack/androidx/releases/compose-ui
- Android Gradle Plugin release notes: https://developer.android.com/build/releases/gradle-plugin
