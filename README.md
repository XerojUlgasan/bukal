# Bukal

Bukal is an offline-first Android study app that turns imported lesson documents into source-grounded quizzes. Quiz generation, answer checking, document search, history, and profile data stay on the device after the required AI models are downloaded.

## Get Bukal

To install Bukal without building it yourself, visit the [official Bukal website](https://bukal-web.zxero.dev/) and select **Download now**.

The website is open source in the [Bukal Web repository](https://github.com/XerojUlgasan/bukal-web).

## Requirements

- Android Studio with Android SDK Platform 37
- JDK 21 configured as the Gradle JDK
- An Android 8.0 (API 26) or newer device or emulator
- Internet access for the initial Gradle dependency sync and model downloads
- At least 1 GB of free device storage for the default quiz and embedding models

A physical Android device is recommended when testing local AI inference.

## Run with Android Studio

1. Clone the repository:

   ```bash
   git clone https://github.com/XerojUlgasan/bukal.git
   cd bukal
   ```

2. Open the repository root in Android Studio.
3. Set the project's Gradle JDK to JDK 21, then let Android Studio sync the project and install any missing SDK components.
4. Connect an Android device with USB debugging enabled, or start an API 26+ emulator.
5. Select the `app` run configuration and click **Run**.

## Run from the command line

Make sure the Android SDK is configured through `ANDROID_HOME` or `local.properties`, then build the debug APK:

```bash
./gradlew assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To install it on a running emulator or a USB-connected device with debugging enabled, confirm that the device is listed and then run:

```bash
adb devices
./gradlew installDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## First launch

1. Keep the device connected to the internet.
2. On **Set up offline AI**, tap **Install required models**.
3. Wait for both **Qwen 3 Compact** and **Granite Embedding 311M R2** to finish downloading and verification.
4. Continue to Home and import a text-based TXT, PDF, DOCX, or PPTX lesson. Scanned or image-only documents are not supported.
5. Select one or more passages, choose quiz types, and start the quiz.

The AI model files are intentionally not included in the repository or APK. Once the required models are installed, the study flow can run without an internet connection.

## Verify the project

Run the local unit tests:

```bash
./gradlew testDebugUnitTest
```

To run Android instrumentation tests on a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Some local-AI device tests require the corresponding `.litertlm` model files to be installed first.

## Troubleshooting

- Confirm Gradle is using Java 21 with `./gradlew --version`.
- If no device is detected, run `adb devices` and accept the USB debugging prompt on the phone.
- If a model download fails, check the device's internet connection, available storage, and Android Download Manager, then retry from the setup screen.
- If Android Studio reports a missing SDK, install Android SDK Platform 37 from SDK Manager and sync again.

More implementation details and the current verification status are in [docs/implementation-checklist.md](docs/implementation-checklist.md).
