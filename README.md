# AI Voice Changer Effects

A public Kotlin Android code sample exploring recording, voice effects, audio playback, and API-backed voice workflows.

## Overview

This README documents the checked-in Android project and provides a practical guide to reviewing, configuring, building, and validating it. Implementation and dependency details come from the repository source and Gradle configuration.

## Feature map

- RecordingActivity, VoiceEffectActivity, ReverseVoice, VoiceAIActivity, and AudioPlayerActivity.
- File, preview, translation, settings, language, and splash activities.
- Separate ViewModel, data, DI, application, UI, and utility packages.
- Coroutines, Hilt, Retrofit/Gson, OkHttp, waveform UI, Glide, and Lottie dependencies.

This repository is a code sample. API-dependent flows need valid service configuration; no production-service availability or AI-model ownership is implied.

## Technology stack

- **Language:** Kotlin / Android.
- XML layouts / View Binding
- Hilt dependency injection
- Kotlin Coroutines
- AndroidX Lifecycle / ViewModel
- Retrofit / Gson
- OkHttp
- Glide / Coil image loading

| Build setting | Value |
| --- | --- |
| Application ID | `com.voicechanger.app` |
| Minimum Android API | 24 |
| Compile SDK | 34 |
| Target SDK | 34 |
| Default branch at documentation time | `master` |

The Java/Kotlin bytecode target in the app build is separate from the JDK used to run Gradle. Use a Gradle JDK compatible with the checked-in Android Gradle Plugin and wrapper.

## Project structure

| Location | Purpose |
| --- | --- |
| `app/` | Android application module |
| `app/src/main/` | Main source, resources, and manifest |
| `app/src/test/` | Local JVM test source set, when populated |
| `app/src/androidTest/` | Device/emulator test source set, when populated |
| `gradle/` | Gradle wrapper and dependency configuration |
| Root Gradle files | Plugin, repository, and module configuration |

Main source areas:

- `Viewmodels`
- `activities`
- `androidhilt`
- `data`
- `di`
- `fragments`
- `ui`
- `utils`

## Getting started

1. Clone this repository using an account with access.
2. Open the repository root in Android Studio.
3. Install the SDK platform requested by the checked-in app build; accept dependency sync using a compatible Gradle JDK.
4. Configure the services required by the feature you intend to run, then select the `app` configuration and a device meeting the minimum API requirement.

```powershell
git clone https://github.com/Muhammad-muaz-dev/AIVoiceChangerEffects.git
cd AIVoiceChangerEffects
.\gradlew.bat :app:assembleDebug
```

On macOS/Linux, use `./gradlew :app:assembleDebug` (set executable permission on the wrapper if required). The APK is normally under `app/build/outputs/apk/debug/`. Building the debug variant avoids the release-distribution workflow, but Gradle configuration must still resolve correctly.

## Configuration

- Review `app/build.gradle.kts`, the root build, and `settings.gradle.kts` before changing SDK, plugins, or dependencies.
- Check the data/API or network implementation for service base URLs and required configuration. Use your authorized development environment for API-dependent flows.
- Release signing is a separate setup step. Use authorized signing configuration; credentials and keystore contents are intentionally not reproduced here.

## Validation

Check the test source sets and Gradle tasks before assuming test coverage is available. These commands are a development guide; a successful build or test run is not asserted by this documentation update.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Connected tests require a running emulator or connected device. If a task is unavailable in this checkout, inspect `:app:tasks` and use the matching variant task.

**Manual verification:** Record or select a sample, apply a voice effect, preview/play the result, and inspect the file/translation flows. Test microphone denial, API errors, invalid audio, and screen recreation.

## Troubleshooting

- **Gradle sync fails:** check the first reported error, wrapper/AGP/JDK compatibility, SDK installation, and access to configured dependency repositories.
- **Service-backed feature fails:** confirm configuration and connectivity, then inspect Logcat at the point of failure.
- **Media/storage feature behaves differently on another Android version:** verify runtime permissions and the relevant storage-access behavior on that device.
- **Release build fails:** check signing and release-only configuration independently of debug behavior.

## Development workflow

Keep UI, data, and supporting logic in the existing source organization. For each change, document the trigger and expected result, run relevant checks, and add focused regression coverage where the behavior warrants it. Include screenshots or a short recording for UI changes, using test data.

## Ownership and reuse

Maintained in Muhammad Muaz’s GitHub workspace. See [the developer profile](https://github.com/Muhammad-muaz-dev) for public Android work. Repository visibility does not grant reuse rights; refer to an existing license or obtain permission from the rights holder before redistributing code or assets.
