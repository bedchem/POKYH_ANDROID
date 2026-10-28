# POKYH for Android

POKYH is a school app for students of the Landesberufsschule Brixen (LBS Brixen). It puts
everything from WebUntis in one place (timetable, grades, absences, messages) and adds what
WebUntis does not have: the Mensa menu, to-dos, class reminders and an overview of your own
activity. POKYH exists as a website ([pokyh.com](https://pokyh.com)), an iOS app and this
Android app. All three use the same account and look and work the same way.

This repository is the native Android app, written in Kotlin with Jetpack Compose.

## What the app does

- **Timetable:** substitutions, cancellations and room changes at a glance.
- **Grades:** averages, trends and what you still need, per subject.
- **Absences:** a calculator for how many hours you can still miss.
- **Messages:** read WebUntis messages and write to teachers.
- **Mensa:** the menu of the day, with ratings and comments.
- **To-dos and class reminders:** shared with your class.
- **Notifications:** for new grades, messages, cancelled lessons and reminders. Tapping one
  opens it in the app.
- **Offline:** the app keeps working with the last loaded data and shows how old it is.
- **Accounts and sign-in:** several accounts, fingerprint unlock, and signing in on a computer
  by scanning a QR code.
- **Widgets and languages:** home screen widgets for the next lesson and the latest grades;
  German, Italian and English.

## Screenshots

| Home | Timetable | Grades | Profile |
| --- | --- | --- | --- |
| ![Home](docs/screenshots/home.png) | ![Timetable](docs/screenshots/timetable.png) | ![Grades](docs/screenshots/grades.png) | ![Profile](docs/screenshots/profile.png) |

## Download

Get the latest APK from the [releases page](https://github.com/bedchem/POKYH_ANDROID/releases).
POKYH needs Android 8.0 or newer. Once installed, the app finds new versions on its own.

## Building

1. Copy `local.properties.example` to `local.properties` and fill in the POKYH API keys.
2. Run `./gradlew installDebug`, or open the project in Android Studio.

## Related

- Website: [pokyh.com](https://pokyh.com)
- Web app: [bedchem/pokyh-frontend](https://github.com/bedchem/pokyh-frontend)
- iOS app: [bedchem/POKYH_IOS](https://github.com/bedchem/POKYH_IOS)

POKYH is a student project and is not officially connected to LBS Brixen or Untis.
