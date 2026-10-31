# Mortal TV

Native Kotlin Android TV IPTV player and personal web-link launcher. Designed for a D-pad remote, with local storage and no cloud account requirement.

## Build and install

Requirements: JDK 17, Android SDK platform 36 and build tools 36.0.0. Set `ANDROID_HOME` or create an ignored `local.properties` containing `sdk.dir=C\:/Android/Sdk` (use your SDK location).

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or transfer the APK to the TV and open it with a file manager. A debug build uses a development certificate: production releases signed with a different key cannot upgrade it in place. Export a backup before switching signing identities.

## On your TV

- Use Home, Channels, Sri Lankan TV, Favorites, Recent, Others, and Settings from the sidebar.
- Select **+ Add** to add a playlist URL, direct HTTP/HTTPS stream, pasted M3U, local file, or web link.
- Select a channel to play, open in another player, or favorite it. Use **Groups** and **Sources** to filter/manage the library.
- Player controls include play/pause, seek bar, 10-second skips, live edge, audio/subtitle/video selection, fit/fill, mute, and retry. Seeking depends on the stream's DVR/seekable window. Back hides the player overlay before leaving playback.
- In Others, select a card to open its address. Use the remote Menu button or **+ Add → Manage existing web links** to edit, reorder, pin to app Home, or delete links. The manage menu does not require a Menu key or long-press.
- Install an external player/browser on the TV to use external actions. Some launchers and devices have no browser preinstalled.

The initial UI uses native Android Views and a recycled card grid to keep dependencies and drawing overhead small. Channel logos are cached and decoded at small sizes. There are no decorative animations or previews.

## Manage from a PC

1. On the TV, open **Settings → Connect your PC**.
2. On a PC on the same trusted network, visit the displayed address, such as `http://192.168.1.20:8765`.
3. Enter the TV's pairing code. Add/import sources and manage Others links. Changes appear on TV automatically.
4. Use backup/export to download the library JSON. Stop management in the TV dialog when finished.

The TV app must remain running; force-stopping it stops the server. Guest Wi-Fi/client isolation or a firewall may prevent access. The pairing code expires in 15 minutes and sessions in one hour. Restart management for a new code. Five failed pairing attempts per minute are allowed per client.

This is a local HTTP server: pairing restricts access but does not encrypt traffic. Use a trusted LAN, never port-forward it, and protect backups because stream URLs may contain tokens. Uploads are limited to 8 MB. JSON requests, origin checks, authorization headers, and lack of cross-origin permission protect state-changing endpoints from ordinary cross-site requests.

Importing under the same source name replaces only that source after successful parsing. Empty/failed refreshes retain the prior catalog. Renaming an existing source is intentionally restricted to protect channel identity; add a new named source and delete the old one if required.

## Sri Lankan TV

The built-in section includes official broadcaster pages for ITN, Vasantham, and Rupavahini. These open in a browser; they are not represented as direct Media3 streams. The Rupavahini page currently embeds a YouTube live link. Public pages were researched on November 1, 2026:

- https://itn.lk/live/
- https://vasantham.lk/
- https://tv.rupavahini.lk/live-webcast/rupavahini-live-webcast2/

Use **Settings → Sri Lankan catalog** with a maintained HTTPS M3U catalog, or import your own sources with group `Sri Lanka`, to populate direct playback channels. The imported catalog persists as a last-known fallback and refreshes independently of APK releases. Complete national coverage is not currently bundled or verified. A public webpage is not necessarily a direct playable stream or a license to redistribute its media.

## Signed GitHub releases and app updates

The workflow in `.github/workflows/release.yml` builds/tests on pushes to `main`, signs the APK, and publishes it with `update.json`. Configure a public GitHub repository and these Actions secrets first:

| Secret | Value |
|---|---|
| `SIGNING_STORE_BASE64` | Base64-encoded release keystore |
| `SIGNING_PASSWORD` | Keystore password |
| `SIGNING_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

Generate and back up a private release keystore with `keytool`. Keep the same key for the lifetime of the app. Never commit it or its passwords. The repository ignores common keystore extensions. Local release builds read `SIGNING_STORE`, `SIGNING_PASSWORD`, `SIGNING_ALIAS`, and `SIGNING_KEY_PASSWORD` environment variables.

```powershell
.\gradlew.bat assembleRelease -PversionCode=1001 -PversionName=1.0.1
```

Workflow versions are `versionCode = 1000 + github.run_number`, `versionName = 1.0.<run_number>`. Retries of a published run should use a new workflow dispatch/run rather than overwrite an existing release. Keep the workflow filename/history stable; if migrating workflows/repositories or publishing a higher manual version, increase the version-code base accordingly. Serialized jobs and a latest-version check prevent an older queued workflow from replacing a newer latest release.

In **Settings → App updates**, enter `owner/repository`. The app reads the latest release metadata, downloads the APK, verifies SHA-256, application ID, exact version, and the same signing certificate as the installed app. It invokes Android's installer. Enable installs from Mortal TV if prompted, then choose **Settings → Install downloaded update**. Android requires the user's installation confirmation. The app does not contain GitHub tokens and cannot consume private release URLs without an additional secure delivery service.

The configured repository is `hamdhi/Mortal-T1-mega-app`. App update checks use it by default; Settings can override the repository. Public releases are required for the token-free download flow. The release signing key is stored locally in the ignored `.signing/` directory and configured in the four GitHub Actions signing secrets. Back up this directory securely; losing the key prevents compatible future updates. Never add it to Git.

## Verification and remaining scope

Unit tests cover playlist metadata, quoted commas, BOM/line endings, relative links, signed query strings, duplicate identity, unsafe input, and distinguishing HLS manifests from channel catalogs. Run `testDebugUnitTest lintDebug assembleDebug` for the local checks.

Manual device checks should cover remote-only navigation, refresh failures, large playlists, browser/player intents, live and on-demand playback, PC pairing/import/restore, and upgrading over a previous APK signed with the same certificate. Actual Mortal T1 testing requires device access; a generic emulator does not verify the box's codecs or Bluetooth remote.

XMLTV now/next is available in Settings and maps programmes using playlist `tvg-id`. Plain XML feeds are limited to 8 MB; cached current/upcoming programmes cover up to 48 hours. Optional source refresh runs every six hours while the app remains running. Link actions also offer Android launcher pinning when the launcher supports it; Add to Home pins inside the app.

Remaining scope includes broader verified Sri Lankan direct-stream coverage and remembered external app selection. No silent Android update, recording, or universal codec/DRM support is promised.
