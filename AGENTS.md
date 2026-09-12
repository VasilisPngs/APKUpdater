# Android Project Instructions

## Git & repository workflow
- Push changes directly to `main`; do not open pull requests.
- Publish every completed change set as exactly one commit.

## General rules
- Do the work, build, test, lint and verify when the environment allows it.
- Do not undo settled decisions or weaken requirements without a real technical reason.
- Prefer root-cause fixes and the simplest architecture that fully satisfies the requirements.
- Remove dead code, unused dependencies, resources, permissions and configuration.
- Avoid unnecessary CPU, RAM, GPU, battery, storage, network use, allocations, I/O, polling and redundant state.
- Keep code clean, direct, idiomatic and maintainable.
- All code is written in English.
- Do not add explanatory or redundant comments inside code.
- Use the latest appropriate official technologies, APIs, libraries and tools. Preview, beta, release candidate, alpha and canary are allowed when they are the newest appropriate choice.
- Verify current versions from official sources rather than relying on outdated examples.
- Do not add a numeric product version to the visible product identity, release name, APK filename, release tag, or API User-Agent unless explicitly requested.

## Android
- Use the latest appropriate official Kotlin, Jetpack Compose, Material 3, AndroidX, Android SDK, Android Gradle Plugin, Gradle and supported JDK.
- Treat native Android/Google UI and UX as the default product design standard across the entire application, not only for individual screens or components.
- Use official Android and Material 3 components, layouts, typography, interaction patterns, navigation, loading/progress states, dialogs, controls and other native interaction patterns wherever the platform provides an appropriate solution.
- Prefer platform-provided behavior, visuals and semantics over custom implementations or imitations of native Android UI.
- Use platform defaults and dynamic color where appropriate; do not create custom application-wide palettes or themes.
- Query actual device capabilities and system-provided application/device data instead of assuming manufacturer-specific behavior or duplicating information already exposed by Android.
- Prefer system-provided application metadata such as icons, labels and package information instead of duplicating those values in app resources or hardcoded mappings.
- Prefer system-derived data and behavior over manually recreated or hardcoded equivalents. When the operating system or device can provide a value reliably at runtime, obtain it from the relevant official platform API and use it directly.
- Keep expensive work away from the UI thread.
- Prefer current Android APIs and remove obsolete compatibility layers and workarounds.

## Blur, transparency and system surfaces
- Build blur and transparency with the official platform and Jetpack APIs only; never with a third party effect library or a hand drawn imitation.
- Blur inside the application, such as content that scrolls under a bar, is rendered with `BlurEffect` on a `GraphicsLayer`, backed by the platform `RenderEffect`, which requires Android 12.
- Blur of what lies behind the application window, such as a dialog dimming the screen, uses the cross window blur APIs `Window.setBackgroundBlurRadius`, `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` and `setBlurBehindRadius`, and must query `WindowManager.isCrossWindowBlurEnabled` and register the listener, because the system disables these blurs on battery saver, on low graphics performance and by developer override.
- Translucent surfaces stay readable when blurs are unavailable: pair them with the Material container colors instead of relying on the effect alone.
- Draw edge to edge and let the window insets place the content, rather than reserving space manually.

### Official references
- Graphics modifiers: https://developer.android.com/develop/ui/compose/graphics/draw/modifiers
- Graphics in Compose: https://developer.android.com/develop/ui/compose/graphics/draw/overview
- BlurEffect: https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/BlurEffect
- GraphicsLayer: https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer
- RenderEffect: https://developer.android.com/reference/android/graphics/RenderEffect
- Window: https://developer.android.com/reference/android/view/Window
- WindowManager: https://developer.android.com/reference/android/view/WindowManager
- Window blurs: https://source.android.com/docs/core/display/window-blurs
- Window insets: https://developer.android.com/develop/ui/compose/system/insets
- Edge to edge: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- App bars: https://developer.android.com/develop/ui/compose/components/app-bars
- Material Design 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Dynamic color: https://developer.android.com/develop/ui/views/theming/dynamic-colors

## Verification
- Before considering a change complete, verify build/lint results, dependencies, resources, release configuration and relevant runtime behavior.
