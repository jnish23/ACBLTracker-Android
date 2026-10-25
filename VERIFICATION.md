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
