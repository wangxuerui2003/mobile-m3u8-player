# AGENTS

## Project Summary

This repository contains a native Android app for browsing and playing M3U8/HLS video sources backed by the `okzyw.net` API. The app is built with Jetpack Compose, Hilt, Room, Retrofit, and Media3.

## Tooling

- Use `JDK 17`.
- Use the checked-in Gradle wrapper: `./gradlew`.
- Main verification commands:
  - `./gradlew assembleDebug`
  - `./gradlew lintDebug`

## Code Map

- `app/src/main/java/com/example/m3u8player/data`
  - API, Room DAO/database, preset bootstrap data, repository logic.
- `app/src/main/java/com/example/m3u8player/ui`
  - Compose screens, navigation, player UI, and feature ViewModels.
- `app/src/main/java/com/example/m3u8player/service`
  - Foreground data-update service and foreground download engine/coordinator.
- `app/src/main/assets/preset_videos.json`
  - Seed data loaded on first launch.
- `okzyw_fetcher.py`
  - Standalone helper script. Treat it as separate from the Android runtime unless the task explicitly involves the fetch pipeline.

## Working Rules

- Do not remove the preset data bootstrap unless you also replace first-launch data population.
- Prefer fixing issues through `VideoRepository` and `VideoDao` rather than bypassing the data layer in UI code.
- Keep download state and persisted metadata in sync. If you change `DownloadEngine`/`DownloadCoordinator`, verify DB status updates and local file cleanup behavior.
- Preserve `MainActivity` notification permission handling and `DataUpdateService` foreground-service behavior for Android 13+ and API 24+ compatibility.
- Keep launcher icon resources compatible with both adaptive icon devices and pre-Android 8 devices.

## High-Risk Areas

- `PlayerScreen.kt`
  - Playback state is updated in Compose and watch history is saved on dispose.
- `DownloadEngine.kt`
  - Relative playlist URLs, nested playlists, and progress bookkeeping are easy to break.
- `DataUpdateService.kt`
  - Must remain safe on API 24+ while still showing a foreground notification.
- `VideoRepository.kt`
  - Several UI features depend on repository helpers instead of direct DAO access.

## Change Checklist

- If you touch playback or downloads, run `./gradlew assembleDebug`.
- If you touch manifests, themes, notifications, or resources, run `./gradlew lintDebug`.
- If the work modifies database entities or schema, review migration impact and preset bootstrap assumptions before shipping.
