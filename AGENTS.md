# Project Instructions

## Git & Repository Workflow
- For git operations and GitHub updates on this repository, push direct commits to the `main` branch instead of opening Pull Requests.

## Release & Versioning Rules
- The release version is fixed to `1.0.0` (`APKUpdater-1.0.0`). Do not bump or change this version unless explicitly requested by the user.
- GitHub Actions workflow is set up at `.github/workflows/release.yml` to automatically build and publish `APKUpdater-1.0.0` to GitHub Releases on push to `main` or manual trigger.

# Rules

These are standing decisions, not suggestions. They apply everywhere unless
explicitly changed.

## General rules

- **Answer in the language requested by the user.**
- **Do the work, all of it.** Implement, build, test and verify everything that
  can be done from the available environment.
- Do not undo a decision that has already been settled.
- Do not weaken requirements without a real technical reason.
- Do not silently replace a technically possible requested solution with an
  easier but different one.

## Engineering

- Prioritize efficiency, optimization, smoothness, responsiveness and
  reliability.
- Avoid unnecessary latency, bottlenecks, conflicts, allocations, I/O, polling,
  duplicated work and redundant state.
- Do not waste CPU, RAM, GPU, battery, storage or network resources.
- Do not over-engineer. Use the simplest architecture that fully satisfies the
  requirements.
- Prefer fixing root causes over patches and special cases.
- Keep components, modules, dependencies, state, background work and system
  integration coherent.
- UI and UX must feel smooth, responsive, stable and visually coherent.
- Do not optimize blindly. Preserve correctness while improving meaningful
  performance or resource characteristics.
- Avoid legacy baggage of every kind, including obsolete APIs, deprecated
  patterns, dead abstractions, unnecessary compatibility layers and historical
  workarounds.

## Code

- **All code is written in English.**
- Do not add explanatory prose or unnecessary comments inside code.
- Do not use comments to narrate implementation or restate obvious code.
- Keep names precise, consistent and idiomatic.
- Keep code clean, direct and maintainable.
- Remove unused code, dependencies, resources, permissions and configuration.

## Technologies and versions

- Use the latest appropriate official technologies, APIs, libraries, patterns
  and tools.
- Stable is not mandatory. Preview, beta, release candidate, alpha and canary
  are allowed and preferred when they are the newest appropriate choice.
- Verify current versions from official sources instead of relying on memory or
  outdated examples.
- When an upgrade requires related components to be upgraded, update the
  compatibility chain.
- Prefer official platform solutions over custom or third-party replacements
  when the platform already provides the required capability.

## Releases

- Releases must be reproducible.
- Keep build and release configuration deterministic.
- Keep platform-specific release requirements within the relevant platform.

## What cannot be done

Ask the user only for actions explicitly blocked by the environment.

---

# Android

These rules apply only to Android.

## Stack

Use the latest appropriate official:

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- Android SDK
- Android Gradle Plugin
- Gradle
- supported JDK

## Official references

- Android:
  <https://developer.android.com/>
- Material 3:
  <https://developer.android.com/develop/ui/compose/designsystems/material3>
- Compose components:
  <https://developer.android.com/develop/ui/compose/components>
- Accessibility:
  <https://developer.android.com/develop/ui/compose/accessibility>
- Edge to edge:
  <https://developer.android.com/develop/ui/compose/system/setup-e2e>
- Window insets:
  <https://developer.android.com/develop/ui/compose/system/insets>
- Adaptive apps:
  <https://developer.android.com/develop/ui/compose/layouts/adaptive/get-started-with-adaptive-apps>
- Lists:
  <https://developer.android.com/develop/ui/compose/lists>
- State:
  <https://developer.android.com/develop/ui/compose/state>
- Side effects:
  <https://developer.android.com/develop/ui/compose/side-effects>
- AndroidX versions:
  <https://developer.android.com/jetpack/androidx/versions/all-channel>
- Android Maven repository:
  <https://dl.google.com/dl/android/maven2>

## UI

Use official Android and Material 3 components and interaction patterns.

Prefer existing Material components over custom replacements.

Use:

- Material 3 navigation
- Material 3 components
- adaptive layouts
- Android accessibility
- Android-native interaction patterns
- Material Symbols / official Material icons

Do not copy Windows or browser UI patterns into Android.

## Icons and type

- Use Material Symbols, Outlined, wherever an appropriate official symbol
  exists.
- When a vector path is required directly, use the official Material Symbols
  source rather than redrawing it.
- Use the current Android/Material typography system and defaults.

## Theme

Use current default Android/Material 3 theme behavior.

- Do not create a custom application-wide color palette.
- Do not use custom theme colors.
- Use dynamic color where appropriate.
- Respect Android light/dark theme behavior.

## Device compatibility

Query actual capabilities instead of assuming them.

Do not create manufacturer, model or brand-specific hacks.

Handle the general platform case and accept genuine hardware capability
differences when the platform reports them.

## Adaptive behavior

Support relevant Android window sizes and configurations using current Android
adaptive APIs and patterns.

## Accessibility

Support TalkBack, semantics, appropriate touch targets and content descriptions
where required.

## Performance

Avoid unnecessary recompositions, polling, timers, background work, allocations
and redundant UI updates.

Keep expensive work away from the UI thread.

## Camera and media

When camera or media capabilities are involved:

- Query actual device capabilities.
- Prefer the highest supported quality where quality is the requirement.
- Photos: HEIF/HEIC first, JPEG when necessary.
- Video: HEVC or AV1 where available, H.264 otherwise.
- Avoid unnecessary re-encoding, transcoding and downscaling.
- Do not add RAW/DNG unless required.

Relevant official references:

- CameraX:
  <https://developer.android.com/media/camera/camerax>
- Camera2:
  <https://developer.android.com/media/camera/camera2>
- Supported devices:
  <https://developer.android.com/media/camera/supported-devices>
- Low light boost:
  <https://developer.android.com/media/camera/lowlight/choose-option>
- Ultra HDR:
  <https://developer.android.com/media/grow/ultra-hdr>
- Supported media formats:
  <https://developer.android.com/media/platform/supported-formats>
- AI enhancement:
  <https://developer.android.com/media/ai-enhancement/overview>
- Media3 Transformer:
  <https://developer.android.com/media/media3/transformer>
- ML Kit:
  <https://developers.google.com/ml-kit>
- MediaPipe:
  <https://developers.google.com/mediapipe>

## Google's own code

Use Google's published source implementations as references when directly
relevant.

- Jetpack Camera App:
  <https://github.com/google/jetpack-camera-app>
- libultrahdr:
  <https://github.com/google/libultrahdr>
- Filament:
  <https://github.com/google/filament>
- Google open-source projects:
  <https://github.com/google>

## Geometry

Use Android/Material defaults rather than arbitrary dimensions.

- Let official components determine their own dimensions where possible.
- Use `MaterialTheme.shapes`.
- Follow Material spacing conventions.
- Respect minimum touch-target requirements.
- Do not manually duplicate component dimensions without a reason.
- Use current edge-to-edge and inset guidance.
- Use custom visuals only when Material has no appropriate native component.

## Build and verification

Use the latest appropriate Android toolchain.

Before pushing when possible:

- build
- test
- lint/static analysis where configured
- inspect warnings and errors
- verify dependencies
- verify resources
- verify release configuration
- verify accessibility
- verify adaptive behavior
- verify performance-sensitive paths

---

# Windows

These rules apply only to Windows desktop applications.

## Stack

Use the latest appropriate official Microsoft stack:

- C#
- XAML
- WinUI 3
- Windows App SDK
- Fluent Design / current Fluent guidance
- official WinUI controls
- official Windows resources
- Fluent System Icons

## Official references

- Windows:
  <https://learn.microsoft.com/windows/>
- Windows app development:
  <https://learn.microsoft.com/windows/apps/>
- Windows design:
  <https://learn.microsoft.com/windows/apps/design/>
- Design guidelines:
  <https://learn.microsoft.com/windows/apps/design/guidelines-overview>
- Design principles:
  <https://learn.microsoft.com/windows/apps/design/design-principles>
- Navigation:
  <https://learn.microsoft.com/windows/apps/design/basics/navigation-basics>
- Accessibility:
  <https://learn.microsoft.com/windows/apps/design/accessibility/accessibility-overview>
- Inclusive design:
  <https://learn.microsoft.com/windows/apps/design/accessibility/designing-inclusive-software>
- WinUI:
  <https://learn.microsoft.com/windows/apps/winui/>
- WinUI 3:
  <https://learn.microsoft.com/windows/apps/winui/winui3/>
- Windows App SDK:
  <https://learn.microsoft.com/windows/apps/windows-app-sdk/>
- Windows App SDK downloads:
  <https://learn.microsoft.com/windows/apps/windows-app-sdk/downloads>
- Windows SDK:
  <https://learn.microsoft.com/windows/apps/windows-sdk/>
- Versioning:
  <https://learn.microsoft.com/windows/apps/get-started/versioning-overview>
- Windowing:
  <https://learn.microsoft.com/windows/apps/develop/ui/windowing-overview>
- Windows App SDK app structure:
  <https://learn.microsoft.com/windows/apps/develop/ui/windows-app-sdk-app-structure>
- Fluent:
  <https://fluent2.microsoft.design/>
- Fluent System Icons:
  <https://github.com/microsoft/fluentui-system-icons>

## UI

Use real Windows-native UI.

Do not build UI that merely looks like Windows.

Prefer official WinUI controls and Windows patterns for:

- navigation
- menus
- dialogs
- commands
- keyboard and mouse interaction
- windowing
- accessibility

Use appropriate native controls such as `NavigationView`, `CommandBar`,
`ContentDialog`, `InfoBar`, `Flyout`, `MenuFlyout`, `Expander` and other
official WinUI controls.

Do not reproduce Android UI patterns on Windows.

## Icons

Use Fluent System Icons / official Windows iconography.

Do not use Android Material icons solely for visual consistency.

## Theme

Use current default WinUI/Fluent theme resources.

- Do not create a custom application-wide color palette.
- Do not use custom theme colors.
- Respect Windows light/dark theme behavior.
- Respect system accent behavior where supported.

## Typography

Use current Windows/Fluent typography conventions.

## Desktop behavior

Treat these as first-class:

- mouse
- keyboard
- hover
- focus
- tab navigation
- appropriate shortcuts
- context menus
- resizing
- maximize/minimize/restore
- DPI scaling
- different resolutions
- different window sizes

Do not treat Windows as a stretched mobile interface.

## Accessibility

Support Narrator, keyboard navigation, focus visibility, accessible names,
automation properties where required and correct tab order.

## Architecture

Use Windows-specific implementation for:

- WinUI/XAML
- Windows App SDK
- navigation
- windowing
- system integration
- Windows services
- Windows persistence

Do not force generic or mobile abstractions where Windows already provides a
native solution.

## Performance

Avoid unnecessary rendering, timers, background work, allocations, polling and
redundant UI updates.

Keep the UI thread responsive.

## Build and verification

Use the latest appropriate .NET SDK, C# version, Windows SDK, Windows App SDK
and NuGet dependencies.

Before pushing when possible:

- build
- test
- inspect warnings and errors
- verify XAML/resources
- verify accessibility
- verify resizing/scaling
- verify keyboard/mouse behavior
- verify packaging
- verify CI

---

# Chrome Extensions

These rules apply only to Chrome extensions.

## Stack

Use the latest appropriate official Chrome Extensions platform and APIs.

## Official references

- Chrome Extensions:
  <https://developer.chrome.com/docs/extensions>
- Get started:
  <https://developer.chrome.com/docs/extensions/get-started>
- Develop:
  <https://developer.chrome.com/docs/extensions/develop>
- How-to:
  <https://developer.chrome.com/docs/extensions/how-to>
- API reference:
  <https://developer.chrome.com/docs/extensions/reference/api>

Follow the current supported Manifest version and extension architecture.

## Platform APIs

Prefer official Chrome extension APIs over third-party replacements.

Follow the current documented behavior for:

- Manifest
- permissions
- service workers
- content scripts
- extension pages
- messaging
- storage
- lifecycle
- security

Do not use obsolete APIs when a current supported solution exists.

## Permissions

Request only the permissions actually required.

Remove permissions when their associated capability is removed.

## Architecture

Keep appropriate separation between:

- service worker/background logic
- content scripts
- extension pages
- popup/options UI
- storage
- messaging
- shared logic

Respect Chrome execution and security boundaries.

## UI

Use modern web standards and browser-native interaction appropriate to the
extension surface.

Do not copy Android Material UI or Windows WinUI UI into the extension.

## Security

Follow Chrome's current security model.

Treat external data as untrusted.

Do not weaken security for convenience.

## Performance

Avoid unnecessary polling, messaging, DOM updates, storage operations,
content-script work, network requests and background processing.

## Verification

Before pushing:

- validate the manifest
- build/package
- test extension contexts
- test service-worker behavior
- test content scripts
- test messaging
- test permissions
- test storage
- inspect runtime errors
- verify accessibility
- verify performance-sensitive paths

---

# Cross-platform architecture

These rules apply only when functionality exists across multiple platforms.

## Shared functionality

Share only genuinely platform-independent functionality.

Examples:

- domain logic
- business rules
- models
- validation
- parsing
- networking where truly platform-independent
- repositories and interfaces
- filtering/blocking logic where truly platform-independent
- configuration
- statistics and activity models

The shared core must not depend on platform UI frameworks.

## Platform-specific implementation

Keep platform-specific UI and system integration inside the relevant platform.

Android owns Android UI and Android system integration.

Windows owns Windows UI and Windows system integration.

Chrome Extensions own extension UI and Chrome-specific integration.

## UI parity

Platforms may share functionality, terminology and product concepts, but they do
not need identical UI.

**Pixel-perfect parity is not a goal.**

Each platform must use its own official design system, components, interaction
patterns, icons, typography and theme behavior.

## No custom cross-platform design system

Do not create generic components, themes, typography systems, spacing systems or
color systems whose purpose is to make different platforms look the same.

Use the actual platform systems.

# Verification and cleanup

Before considering a task complete:

- build affected targets
- run relevant tests
- inspect warnings and errors
- check dependency resolution
- remove dead code
- remove unused dependencies, resources and permissions
- remove obsolete APIs and workarounds
- check for accidental platform coupling
- check accessibility
- check performance-sensitive paths
- verify documentation when architecture changes
