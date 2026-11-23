# Calendar for Android

A dark-themed Android app for notes, birthday reminders, personal holidays, and nationwide public holidays.

## Features

- Long and quick notes in separate columns.
- Daily reminders and reminders at a specific date and time.
- Annual birthday and personal holiday reminders.
- Public holiday calendars with country selection and offline caching.
- Multiple reminders on the day, one day before, one week before, or 1–365 days before an event.
- Descriptions and a 24-hour time picker.
- English, Russian, and Ukrainian interface options.
- Local storage and reminder restoration after restart.

Public holiday reminders apply to the selected date and year. Ukrainian public holidays follow Article 73 of the Labor Code under ordinary rules, excluding temporary martial-law restrictions. Other country calendars use nationwide public-holiday records from Nager.Date.

## Installation

Download Calendar-1.0.9.apk from the repository Releases page and open it on an Android 8.0 or newer device. Allow notifications and exact alarms when prompted by the app.

## Development

Open this directory in Android Studio and configure a local Android SDK. Build and check the project with the bundled Gradle wrapper.

```powershell
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
```

Release signing keys are managed locally. The distributed APK uses the existing development signing certificate for compatibility with earlier installations.

## Validation

Version 1.0.9 passed release compilation, Android lint, and 10 JVM tests. UI behavior and notification delivery were not validated on a physical phone during this update.

## Data source

Country holiday data is provided by [Nager.Date](https://nagerholidays.com/api).
