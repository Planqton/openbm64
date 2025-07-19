# openBM64

Simple Android app to read a single blood pressure measurement from a paired Beurer BM64 Bluetooth monitor.

## Prerequisites

- Android Studio Bumblebee or newer
- Android SDK with API level 33
- Gradle 8.0+ (handled by the Gradle wrapper)

## Building

Clone the repository and open it in Android Studio. The project uses the Gradle wrapper so no additional setup is required. To install on a device:

1. Connect an Android phone with developer mode enabled.
2. Select `Run > Run 'app'` in Android Studio.

Press the button on the main screen to connect to the paired BM64 and log the values of the next measurement transmitted by the device.

## Python helper

The `scripts/bm64_log.py` script demonstrates how to read a single measurement with the `bleak` library and store it into an Excel sheet.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
