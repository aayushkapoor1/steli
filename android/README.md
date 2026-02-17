# Steli (Android)

Android app for Steli – follows the **Android Toolchain** workshop setup.

## Setup (workshop flow)

1. **Install Android Studio** (latest version). Ensure ~20 GB free disk space.
2. **Open this project**: In Android Studio, **File → Open** and select the `android` folder (this directory).
3. **Gradle sync**: When prompted, click **Sync Now**, or use **File → Sync Project with Gradle Files**. Wait for sync to finish (first time may take a few minutes).
4. **SDK**: Minimum SDK is 24; target/compile SDK is 35. Manage SDKs via **Tools → SDK Manager** if needed.

## Project structure (workshop)

- **app**: The main app module.
- **Gradle Scripts**: `build.gradle.kts` at project root and in `app/`; `gradle/libs.versions.toml` for unified versions.
- **app/src/main/kotlin/.../MainActivity.kt**: Single Empty Activity (Jetpack Compose).
- **app/src/main/res**: Resources (strings, colors, theme).

Switch to **Project** (or **Project Files**) view in the project tool window to see the real file layout.

## Run the app

1. Select a **device** (physical phone with USB debugging, or an emulator from **Tools → Device Manager**).
2. Click the **Run** (green triangle) button in the toolbar.
3. Configuration should be the **app** module.

## Backend

The app calls the Python backend at `http://10.0.2.2:8000` (emulator). Start the backend from the repo root with `cd backend && uvicorn app.main:app --reload`. For a physical device, set `BASE_URL` in `ApiClient.kt` to your machine’s IP.

## Requirements

- **Java 17** for the Android Gradle Plugin. Use Android Studio’s embedded JDK or set `JAVA_HOME` to a JDK 17+ installation.
