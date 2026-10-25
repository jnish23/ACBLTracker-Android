# Build and verification

Verified October 23, 2026 (Pacific time).

- `assembleDebug`, `testDebugUnitTest`, and `lintDebug`: successful.
- 30 unit tests passed: 19 scoring/lineup/date tests, 5 persistence tests, 6 ESPN fixture tests.
- Android lint: 0 errors; 3 English-text localization warnings.
- APK signature verified with Android SDK `apksigner`.
- Installed and launched on an Android 15 / API 35 x86_64 emulator (Pixel 6 profile).
- Live ESPN directory returned 518 players; searched for Jalen Brunson, selected league position G, and added him to the first guard slot.
- Real ESPN scores loaded: Game 1 21 points, Game 2 19 points, weekly total 40.
- Verified installation as an update preserves the saved roster.
- History showed the saved 40-point week with the correct 21/19 breakdown.
- Disabled emulator Wi-Fi and mobile data, force-stopped the app, then restarted. The saved roster and scores remained visible with an offline notice.
- Selected October 23 in Nightly while offline: Jalen Brunson, G2 at Boston, Final, 19 counted points; night total 19.
- No Android runtime crashes observed during these checks.

Preview screenshots in `dist/` show the emulator's test roster. New installations start with an empty roster. No physical Android device has been tested in this workspace. Notification delivery timing under real-device battery restrictions has not been verified.

The local toolchain, emulator image, caches, and development signing key live under `.tools/` and are excluded from the portable source archive. Preserve `.tools/android-user/debug.keystore` in this workspace for compatible APK updates.

## GitHub updates — October 24, 2026

- Public source and releases: https://github.com/jnish23/ACBLTracker-Android.
- 35 local unit tests passed; release compilation and lint passed with 0 errors (English-text and legacy backup-configuration warnings remain).
- GitHub Actions checked, built, signed, verified, and published release **1.1.3**, version code **1003**: https://github.com/jnish23/ACBLTracker-Android/actions/runs/40155681611.
- The original signing key is held only in this workspace and the encrypted `ANDROID_KEYSTORE_BASE64` Actions secret, and is excluded from Git and downloadable source archives.
- Independently verified the published APK's SHA-256 against `update.json`; CI also verified its certificate matches the original APK.
- Tested the full updater path on Android 15: App updates → Check now → offer for 1.1.3 → verified download → Android's allow-installs settings → Android's update confirmation → successful installation.
- Android reported installation success, installed version 1.1.3 / code 1003, with no runtime crash.
- Reopened the updated app: Jalen Brunson remained rostered; saved weekly total 40 and Game 1 / Game 2 scores 21 / 19 remained intact.
- The installer-stream issue found during the earlier 1.1.2 test is fixed in 1.1.3. Use the latest release.

The updater checks when the app opens, at most once daily unless requested manually. Updates require Android's installation confirmation. This was verified in an emulator; a physical phone has not been connected.

## Reserve scores and player news — October 24, 2026

- 47 unit tests passed; release compilation and lint passed with no errors.
- GitHub built and published **1.1.4**, version code **1004**: https://github.com/jnish23/ACBLTracker-Android/actions/runs/40157325307.
- Emulator-only 14-player fixtures verified the display and totals independently: a deep reserve displayed 43 / 33 in gray while the team stayed at 65 / 65 (130 total). Promoting that reserve displayed 48 in white, including the +5 starter bonus, and the first game totaled 103. Backfilling a playing-sub spot displayed 22 in white and the first game totaled 82. The uncounted second game stayed gray throughout.
- The test fixtures were never committed, published, or applied to a physical phone. The emulator's original roster was restored after the tests.
- Live ESPN overview data loaded for Jalen Brunson in both the roster News tab and the player detail news section, including a dated RotoWire fantasy update and ESPN media links.
- Confirmed that Read on ESPN launches the player's HTTPS ESPN page. News filtering, deduplication, date parsing, safe links, summary limits, caching, and forced refresh are covered by tests.
- Offline refresh showed a retry message while retaining loaded articles; reconnecting and refreshing restored the news without a runtime crash.
- The published APK checksum was verified against the release manifest and its signing certificate matches previous versions.
