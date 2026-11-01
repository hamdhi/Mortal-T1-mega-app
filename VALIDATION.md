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

## Player update 1.0.4

- Signed build, all 14 unit tests, and lint passed (zero errors, 30 warnings).
- Video occupies the full playback window; controls and channel status are overlays rather than a permanently reserved bottom strip.
- After 12 seconds without input, accessibility inspection found no player buttons and a screenshot showed unobstructed video across the screen.
- Pressing remote OK restored the controls while the media session remained PLAYING. The same input did not pause the video.
- The restored controls showed `Screen: Stretch`, confirming the default. The Screen action also offers Fit and Zoom.
- Controls hid again after inactivity. Controller animation is disabled, and visibility callbacks are guarded so hiding cannot immediately reopen the overlay.

## Movies and DVR update 1.0.5

- Signed release build passed all 23 unit tests and lint (zero errors, 30 warnings). Added tests cover DVR boundaries, midnight/noon, and catalog validation.
- Installed over the previous signed emulator app without clearing its library. All nine navigation destinations fit at 1280x720; Free Movies shows official Plex, Classic Cinema, and Roku cards with regional/app-browser labels.
- Imported a VOD source through the paired PC server; both its source and channel retained `kind=vod` in the persisted library. Existing sources default to live TV.
- Started Sita Sings the Blues in the built-in player and observed video and a PLAYING media session. This verifies startup, not an uninterrupted viewing of the full film. Other bundled movie endpoints were checked for availability; complete playback and device codecs remain unverified.
- The DASH-IF live simulator with `tsbd_3600` exposed a one-hour live window. Manual rewind moved from approximately 59 minutes to 29 minutes; the TV clock moved back 30 minutes.
- Opened the 12-hour picker with AM/PM, traversed focus to AM using keyboard navigation, and confirmed 4:10 AM. Playback moved to the matching broadcast time in the window. Out-of-window rejection is covered by unit tests. Full Bluetooth-remote picker interaction still needs Mortal T1 testing.
- Official service links are browser/app launchers, not verified direct IPTV streams. No free HBO subscription, free Batman v Superman, universal regional access, or rewind history absent from the source is claimed.

## Quality filter update 1.0.6

- Signed release packaging, all 27 unit tests, and lint passed. Four new tests cover explicit resolutions/dimensions, aliases and group fallback, ambiguous/unlabeled channels, filtering, and additional numeric options.
- Upgraded the existing emulator installation without clearing its library. The Quality button fits beside Add, Groups, Sources, and Refresh at 1280x720.
- D-pad/OK selected 1080p, producing a clear no-match state for the unlabeled fixture library. Scrolled the dialog to its final options with D-pad and selected Unknown; all three unlabeled live fixture channels reappeared.
- Returning from playback retained Unknown. Changing the destination to VOD reset the selection to All. Actual Mortal T1/Bluetooth remote verification remains outstanding.
- List quality comes from playlist labels, not stream probing. SD and Unknown remain separate; service/browser cards are excluded while a quality filter is active. The player still controls actual available adaptive renditions.

## Paired PC APK upload update 1.0.7

- Signed release build, all 35 tests, and lint passed (zero errors, 34 warnings). The PC page JavaScript passed a syntax check. Eight new tests cover bounded-memory streaming, truncated input, invalid lengths, space/reserve checks without integer overflow, large declared sizes, and self-update certificate/version rules.
- APK uploads have no fixed size cap. Available storage minus a 16 MB reserve is enforced, using 64-bit lengths. Unit tests accept a 5 GB length with sufficient space; an actual multi-gigabyte APK was not transferred.
- Against the emulator's real paired server: unauthenticated upload returned 401, cross-origin upload returned 403, and invalid APK input returned 400. An interrupted request left no pending APK. A second upload was rejected while an APK was already pending.
- Uploaded the 5,428,983-byte signed 1.0.7 APK. The TV displayed Mortal TV, package `lk.mortal.tv`, and version/code 1.0.7/1007. The TV Install action opened Android's package installer, which asked for Update confirmation and then reported **App installed**.
- Reopened Mortal TV after installation, paired again, and compared the persisted library against its pre-upload export: unchanged. The older previously published Mortal TV APK was rejected with an older-version error.
- Used D-pad/OK to open Remove APK and confirm removal; Settings then reported no uploaded APK. The installed app remained available.
- The served PC page includes Upload APK, storage availability, progress/cancel controls, and Show install prompt on TV. Upload transport and installation were exercised through HTTP and native UI; browser-specific progress/cancel rendering and Mortal T1 hardware behavior still need device/browser checks. Android controls final compatibility and installation approval; no silent installation is claimed.
