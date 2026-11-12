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

## CI and releases

Everything runs in `.github/workflows/android.yml`.

**CI.** Every push to `main` and every pull request runs the unit tests and builds a debug APK.
It needs no secrets. The debug APK is kept as a workflow artifact for 7 days.

**Release.** Publish a GitHub release with a tag `vX.Y.Z` (minor and patch 0-99):

```sh
gh release create v2.3.0 -R bedchem/POKYH_ANDROID --title v2.3.0 --notes "..."
```

The workflow takes the version from the tag, runs the unit tests, builds and signs the APK,
checks package, version and signature, and attaches `POKYH-X.Y.Z.apk` plus a `.sha256` file to
the release. Installed apps pick it up from there. The release must not have any other `.apk`
attached; the workflow refuses to add a second one.

A manual run (Actions > Android > Run workflow, or
`gh workflow run android.yml -R bedchem/POKYH_ANDROID -f version=2.3.0`) builds the same signed
APK but only keeps it as a workflow artifact for 30 days.

### Secrets and variables

Only the release build uses them. The APK has to be signed with the key every earlier release
was signed with (the maintainer's `~/.android/debug.keystore`), otherwise Android refuses the
update. A release fails if any secret is missing; a manual run only warns about the two API keys.

| Name | Kind | What goes in |
| --- | --- | --- |
| `POKYH_KEYSTORE_BASE64` | secret | The release keystore file, base64-encoded |
| `POKYH_KEYSTORE_PASSWORD` | secret | Its store password |
| `POKYH_KEY_ALIAS` | secret | Alias of the signing key in it |
| `POKYH_KEY_PASSWORD` | secret | Password of that key |
| `POKYH_API_KEY` | secret | The backend's `API_KEY` |
| `POKYH_SERVER_KEY` | secret | The backend's `SERVER_KEY` |
| `POKYH_SIGNING_CERT_SHA256` | variable, optional | SHA-256 of the signing certificate; when set, a release signed with any other certificate fails |

```sh
base64 -i <path/to/keystore> | gh secret set POKYH_KEYSTORE_BASE64 -R bedchem/POKYH_ANDROID
gh secret set POKYH_KEY_ALIAS -R bedchem/POKYH_ANDROID --body '<key alias>'
# These prompt for the value, so it stays out of the shell history:
gh secret set POKYH_KEYSTORE_PASSWORD -R bedchem/POKYH_ANDROID
gh secret set POKYH_KEY_PASSWORD -R bedchem/POKYH_ANDROID
gh secret set POKYH_API_KEY -R bedchem/POKYH_ANDROID
gh secret set POKYH_SERVER_KEY -R bedchem/POKYH_ANDROID
gh variable set POKYH_SIGNING_CERT_SHA256 -R bedchem/POKYH_ANDROID --body '<sha256>'
```
The certificate SHA-256 of a published APK is printed by
`apksigner verify --print-certs POKYH-X.Y.Z.apk` (and by every release run).

## Related

- Website: [pokyh.com](https://pokyh.com)
- Web app: [bedchem/pokyh-frontend](https://github.com/bedchem/pokyh-frontend)
- iOS app: [bedchem/POKYH_IOS](https://github.com/bedchem/POKYH_IOS)

POKYH is a student project and is not officially connected to LBS Brixen or Untis.
