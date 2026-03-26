# Android app

This directory contains the native Android client for Task Manager.

It shares the same backend and task model as the web app, but the runtime model is different:

- the backend is still the source of truth
- Room is the local cache
- pending offline changes are stored in an outbox and synced later

If you are new to the whole repository, start with the root [`README.md`](../../README.md) first. This file focuses only on Android-specific setup and behavior.

## What the Android app supports

- task views: `All`, `Today`, `Tomorrow`, `Inbox`, and per-list views
- calendar view
- task and list CRUD
- one-level subtasks
- due date, reminder time, repeat, and `repeat_until`
- pinning and manual reorder
- show/hide completed
- new-task placement preference
- offline-first cache and sync queue
- notification settings

## Prerequisites

- Android Studio
- JDK 17
- Android SDK 34

The Gradle wrapper version is `8.7`, and the app uses:

- Android Gradle Plugin `8.5.2`
- Kotlin `1.9.24`
- `minSdk = 26`
- `targetSdk = 34`

## Fastest way to run it

The easiest backend setup for Android is the shared Docker stack from the repo root:

```bash
docker compose up --build
```

Why this is the easiest path:

- the app default base URL is `http://10.0.2.2/api/`
- the current Android URL normalizer expects an `/api/` base path
- the repo Docker stack already exposes that through Caddy on host port `80`

After the Docker stack is up:

1. Open `apps/android` in Android Studio.
2. Sync Gradle if prompted.
3. Start an emulator or connect a device.
4. Run the `app` configuration.
5. In the app, open `Settings` and confirm the API base URL.

## Android Studio flow

1. Open the `apps/android` project.
2. Let Android Studio install any missing SDK components.
3. If Android Studio does not generate `local.properties`, copy `local.properties.example` to `local.properties` and set your local `sdk.dir`.
4. Wait for Gradle sync to finish.
5. Start an emulator or connect a physical device.
6. Run the `app` target.

## CLI build and test

From this directory:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK will be written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` is machine-specific and intentionally gitignored.

## Base URL rules

The app stores the backend base URL in DataStore and normalizes it before creating Retrofit services.

Recommended values:

- Android emulator with the repo Docker stack:
  - `http://10.0.2.2/api/`
- Physical device on the same Wi-Fi/LAN:
  - `http://YOUR_HOST_LAN_IP/api/`

The app will also normalize host-only URLs like `http://192.168.0.10` to `http://192.168.0.10/api/`.

## Networking notes

### `localhost` is wrong on Android

- On an emulator, `localhost` points to the emulator itself.
- On a physical device, `localhost` points to the phone itself.

That is why local backend URLs must use either:

- `10.0.2.2` for the Android emulator
- your machine's LAN IP for a physical device

### Current app expects `/api/`

The current Android client is designed around an `/api/` base path. That matches the repo's Caddy setup.

For that reason, the simplest local Android setup is:

- run the repo Docker stack
- point the app to the host-level `/api/` URL

If you are not using Docker, put the backend behind a reachable `/api/` reverse proxy first. The current Android base-URL normalizer is not built around a raw FastAPI root such as `http://HOST:8000/`.

### Cleartext HTTP is debug-only

Debug builds enable local cleartext HTTP through:

- [`app/src/debug/AndroidManifest.xml`](app/src/debug/AndroidManifest.xml)
- [`app/src/debug/res/xml/network_security_config.xml`](app/src/debug/res/xml/network_security_config.xml)

Do not assume the same behavior for release builds.

## Offline architecture

- Room is the local cache for tasks, lists, pending sync operations, and sync state.
- Cache entries are namespaced by normalized base URL, so switching environments does not mix data.
- Create task and supported completion flows can be queued locally and replayed later.
- Manual sync and background sync replay the outbox and then refresh from the server.
- Backend still wins if there is any conflict after sync.

## Current offline limits

- recurring completion is still online-only
- online-only flows like update, delete, reorder, and move still require a live backend round-trip

## Troubleshooting

### The app cannot connect from the emulator

Check:

- the Docker stack is up
- you used `http://10.0.2.2/api/`
- port `80` on your machine is reachable

### The app cannot connect from a physical device

Check:

- the phone and laptop are on the same network
- you used your machine's LAN IP, not `localhost`
- your firewall allows inbound connections

### The app builds but data never loads

Open `Settings` inside the app and verify the stored base URL. If the URL is wrong, the Room cache can still open, but fresh sync and API requests will fail.
