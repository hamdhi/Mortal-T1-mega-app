# Validation — November 1, 2026

Local delivery: **0.1.3**, Android version code **4**, signed using the local release identity. GitHub Actions assigns its own increasing `1.0.<run>` / `1000 + run` versions using the same signing key.

## Automated checks

- `testDebugUnitTest lintDebug assembleRelease`: passed.
- 14 unit tests passed: eight playlist tests and six update identity/version/certificate policy tests.
- Lint: zero errors; 28 warnings, including dependency updates, native UI text localization, and deprecated compatible Android APIs.
- APK signature verification: passed, APK Signature Scheme v2.
- Git whitespace check: passed. Signing files and temporary test data are ignored by Git.

## Android emulator checks

Environment: generic Android API 35 emulator, landscape display at 1280 × 720. This is not the Mortal T1 or Android TV 16 hardware.

- App install, launch, sidebar navigation, card selection, and playback action selection with D-pad/OK: passed.
- Native TV layout and clear focus highlight: visually inspected.
- Built-in playback of the public ExoPlayer Big Buck Bunny test video: playing state and rendered video verified.
- Remote fast-forward: observed position move from about 14.8 seconds to 27 seconds across a roughly two-second measurement interval, consistent with a 10-second seek.
- An unavailable sample URL returned HTTP 403. The source failure was diagnosed; a separate accessible sample verified playback. Final code keeps the error message visible.
- Signed upgrades from 0.1.0 to 0.1.2 and 0.1.3: installed successfully. Two imported channels and a Home web shortcut remained after upgrade.
- Media3 playback lifecycle and media-session state were inspected; no app crash was reported during these checks.

## PC manager checks

Requests were sent through an ADB-forwarded connection to the running emulator's real local server.

- Pair with the on-screen code: passed.
- Playlist import and Others link creation: passed; UI updated.
- Unauthenticated library access: rejected with HTTP 401.
- Cross-origin state change: rejected with HTTP 403. A subsequent authorized request succeeded after fixing connection closure on rejected requests.
- Empty replacement playlist: rejected with HTTP 400; prior channels retained.
- 1,500 synthetic channels: imported with the expected count in approximately 0.29 seconds on this emulator.
- Export/restore round trip: passed; original two-channel/one-link test library restored after the load test.

## Remaining verification

- Mortal T1 hardware, Android TV 16, Bluetooth remote, device codecs, launcher-specific shortcuts, and external browsers/players need device testing.
- Non-seekable live sources, XMLTV feeds, and all track/subtitle variations have not been exhaustively exercised.
- Complete Sri Lankan direct-stream coverage is not bundled: official browser links and user-supplied/catalog imports are available.
- Full Android installer approval/update interaction should be checked on the actual TV. Unit checks cover identity rejection; signed APK upgrades were tested using ADB.
