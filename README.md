# ACBL Tracker for Android

A native Android port of [jfung-dev/ACBLTracker](https://github.com/jfung-dev/ACBLTracker), the iPhone USACBL fantasy basketball tracker. Built with Java 17 and Android's native UI toolkit. Runs on **Android 8.0 and newer**. No account, subscription, or backend setup required.

## Install on your phone

1. Download `ACBLTracker-android.apk` from the [latest GitHub release](https://github.com/jnish23/ACBLTracker-Android/releases/latest), or copy `dist/ACBLTracker-android.apk` to your Android phone.
2. Open the APK on the phone. If prompted, allow **Install unknown apps** for the app opening the file.
3. Tap **Install**, then open **ACBL Tracker**.

Install updates **over the existing app**, without uninstalling. The release APK uses the same signing identity as the first local APK, so your data stays intact. In this prepared workspace the key is `.tools/android-user/debug.keystore`; keep it for compatible updates. It does not have the original iPhone app's seven-day expiration.

## Automatic builds and phone updates

The public project lives at [jnish23/ACBLTracker-Android](https://github.com/jnish23/ACBLTracker-Android).

1. Change the code and **push to `main`**. Saving a file locally does not publish it.
2. GitHub Actions compiles the app, runs tests and lint, signs the APK, and publishes a versioned release after the checks pass. Pull requests run checks without publishing or accessing the signing key. Documentation-only pushes do not publish a new app; **Run workflow** can publish one manually.
3. Open ACBL Tracker on your phone. It quietly checks once daily and offers newer releases. To check immediately, open **⋯ → App updates → Check now**.
4. Tap **Download update**. The first time, allow ACBL Tracker to install apps in Android settings. Android then asks you to confirm the installation.

The first updater-enabled APK must be installed manually once if you have the original version. Subsequent updates can be installed from inside the app. No GitHub login or token is stored on your phone. The app verifies download size, SHA-256, app ID, version code, and signing certificate before handing the update to Android. Declining an update keeps the current app and data.

The release workflow uses the encrypted repository secret `ANDROID_KEYSTORE_BASE64`. The key is excluded from Git and public release assets. CI app version codes start at 1001 and increase with the workflow run number; rerunning the same workflow retains that run's app version. Do not reset that sequence or replace the signing key for an existing installation.

This updates the app's code. Roster data stays local and does not sync through GitHub.

From this connected workspace, publish changes with:

```powershell
git add .
git commit -m "Describe your change"
git push
```

Use the published APK for phone updates. Local development builds default to a low version code; manually installing them over a newer CI release requires setting a higher `-PversionCode` and using the preserved signing key.

## Use the app

- **Lineup:** tap **+ Player**, search the NBA directory by player or team, then choose the position assigned by your league. Players fill an open starter slot at that position, then bench, then deep bench.
- **Manage players:** tap **Edit**, then tap a player to replace, remove, change league position, or choose a destination. Hold and drag also moves players. Starter moves swap players and enforce positions; reserve moves insert into priority order.
- **Weeks:** arrows navigate Tuesday-based league weeks in Eastern time. Tap the date header to return to this week. Past lineups are read-only; current and future weeks can be edited independently and carry forward until another snapshot exists.
- **Scores:** Game 1 and Game 2 show counted points in white. Uncounted reserves show their actual NBA points in gray, labeled **not counted**; these stay out of all team, night, and history totals. Deep-bench players filling a starter or playing-sub role turn white and show the appropriate full or half points. Bonuses are shown separately underneath and included in counted totals. A played zero differs from **DNP**. Pending games stay provisional. Refresh uses the top-right circular arrow; live scores refresh every minute while the app is open.
- **Nightly:** choose a date to see counting games, roles, and the night's total. Ten dates are available to cover the original app's spillover window.
- **News:** the News tab shows current ESPN news for players on the viewed week's roster, with dates and links to read or watch on ESPN. Direct fantasy updates appear first; other stories match the player's ESPN ID or full name. Refresh reloads the feed; otherwise requests are cached for five minutes. Player details include the same news, with up to five items. News is current even when viewing a past lineup.
- **Player details:** tap a player for counting-game stats, current injury status, news, and recent ESPN games.
- **History:** saved weekly results remain available offline. Final weeks freeze after all counting games settle and the correction window passes.
- **Team name:** tap your team name to rename it across weeks.
- **Reminders:** the top-right menu can enable local notifications one hour before Tuesday's 1 PM Pacific lineup deadline and Sunday's 9 PM Pacific free-agent deadline. Android may delay reminders under battery restrictions. League exceptions still need to be checked manually.

Lineups, directory cache, and results are stored in app-private storage. Uninstalling or clearing app data erases them. No cloud backup or cross-device sync is configured.

## Scoring parity

| Rule | Behavior |
|---|---|
| Roster | 2 G, 2 F, 1 C starters; 3 bench; 6 deep bench |
| Counting games | Each NBA team's first two eligible games from Tuesday; matches the original's 10-day lookup window |
| Starter points | Full NBA points |
| Starter bonus | 30–39: +2; 40+: +5; not stacked |
| DNP starter | First active reserve of the same league position, in bench/deep priority order |
| Promoted reserve | Full points and starter bonus |
| Playing subs | Next three active, unpromoted reserves; half points rounded up; no bonus |
| Remaining reserves | Zero counted points |
| Played zero | Still active and eligible; not treated as a DNP |
| Pending game | Provisionally occupies its role until final data arrives |
| NBA Cup championship | Excluded using ESPN game notes |
| Failed schedule or box score | Refresh fails; saved scores stay visible; no guessed DNP or final result |

ESPN's unofficial public endpoints can change. This app is not affiliated with ESPN, the NBA, or USACBL. Original feature and scoring reference: `original/ACBLTracker/` (a local reference checkout excluded from this project's outputs).

## Build

Open this folder in Android Studio, select JDK 17, install SDK 35, and run the `app` configuration. Or use the included Gradle wrapper:

```powershell
# This prepared workspace includes a local toolchain in .tools/.
./build.ps1 -Check
./build.ps1 -Release -Check

# With JAVA_HOME and ANDROID_HOME configured on another machine:
./gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

On macOS/Linux: `./gradlew assembleDebug testDebugUnitTest lintDebug` (make `gradlew` executable if needed).

`build.ps1` copies the APK to `dist/ACBLTracker-android.apk`. The standard Gradle output is `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

- `League.java`: Android-independent models, position-aware moves, week dates, and scoring.
- `Store.java`: atomic local persistence, per-week snapshots, and results keyed by player ID.
- `EspnClient.java`: bounded concurrent ESPN directory, schedule, box-score, injury, and game-log requests.
- `MainActivity.java`: native Lineup, Nightly, History, player management, and lifecycle-aware refresh.
- `ReminderReceiver.java`: optional local deadline notifications.
- `UpdateManager.java`, `ReleaseInfo.java`, `UpdateInstallReceiver.java`: verified public-release checks, downloads, and Android installation confirmation.
- `.github/workflows/android.yml`: checks on pull requests; signed releases on main-branch pushes.
- `app/src/test/`: scoring, roster, date, persistence, and ESPN fixture tests.

The port uses the original's fixed 2026–27 Week 1 anchor (October 20, 2026), editable in `League.SEASON_START` for another season. It does not import an existing iPhone app's private files automatically.
