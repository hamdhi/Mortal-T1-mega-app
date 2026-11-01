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
- Playback defaults to Stretch, filling the entire screen. The Screen button cycles Stretch, Fit, and Zoom. All player controls hide after 10 seconds without input; press the remote OK button to show them again without pausing the video.
- In Others, select a card to open its address. Use the remote Menu button or **+ Add → Manage existing web links** to edit, reorder, pin to app Home, or delete links. The manage menu does not require a Menu key or long-press.
- Install an external player/browser on the TV to use external actions. Some launchers and devices have no browser preinstalled.

The initial UI uses native Android Views and a recycled card grid to keep dependencies and drawing overhead small. Channel logos are cached and decoded at small sizes. There are no decorative animations or previews.

## Manual TV rewind and 12-hour clock seeking

On a seekable live channel, open the controls and choose **−30 min** to rewind from the current position. This is a manual action; tuning a channel does not jump backward. The source must expose at least 30 minutes before the current position. If it does not, the app explains the available history and leaves playback unchanged. It does not invent footage, record all channels, or bypass provider restrictions.

**Go to time (AM/PM)** uses a 12-hour picker and the broadcast timestamps in the live stream. The player shows the available interval and current TV time in the device's local timezone. A selection near midnight resolves to the most recent matching date within the stream's timeline; times outside the available window are rejected. Sources without broadcast timestamps can still use relative seeking when supported, but cannot offer reliable wall-clock seeking. **Go live** returns to the provider's live edge.

## VOD / movies

The VOD screen separates on-demand movies from live channels. The initial creator-authorized catalog contains the complete features **Sita Sings the Blues** and **Seder-Masochism**, and the complete shorts **Sintel** and **Big Buck Bunny**. Film details show runtime, attribution, authorization information, and the creator/source page. The player supports resume, start over, seeking, favorites, and external players.

This is a starter catalog, not every free film on the internet. Commercial titles such as Batman v Superman are not represented as free without a verified authorized source. Catalog availability and device codec compatibility can vary. No third-party streaming service is scraped or bypassed.

From the PC manager, choose **VOD / movies** as the library destination before importing an M3U or direct movie URL. On the TV, use **+ Add** while viewing VOD. Source refresh, backups, and restores retain that destination. Existing playlists remain live TV after upgrading.

The catalog is bundled from `catalog/vod.json`; **Settings → Refresh free movie catalog** retrieves the current version from the repository while keeping the last valid version if refresh fails. Each catalog entry must include the creator credit and rights/source information. Creator sources checked November 1, 2026: https://sedermasochism.com/download/, https://www.sitasingstheblues.com/watch.html, https://durian.blender.org/download/, and https://peach.blender.org/about/.

## Manage from a PC

1. On the TV, open **Settings → Connect your PC**.
2. On a PC on the same trusted network, visit the displayed address, such as `http://192.168.1.20:8765`.
3. Enter the TV's pairing code. Add/import sources and manage Others links. Changes appear on TV automatically.
4. Use backup/export to download the library JSON. Stop management in the TV dialog when finished.

The TV app must remain running; force-stopping it stops the server. Guest Wi-Fi/client isolation or a firewall may prevent access. The pairing code expires in 15 minutes and sessions in one hour. Restart management for a new code. Five failed pairing attempts per minute are allowed per client.

This is a local HTTP server: pairing restricts access but does not encrypt traffic. Use a trusted LAN, never port-forward it, and protect backups because stream URLs may contain tokens. Playlist/backup JSON requests are limited to 8 MB. APK uploads stream directly to disk without a fixed size cap, limited by available TV storage with a 16 MB reserve. Origin checks, authorization headers, restricted content types, and lack of cross-origin permission protect state-changing endpoints from ordinary cross-site requests.

### Upload and install an APK

After pairing, choose a standalone `.apk` in **Upload APK to your TV**, then **Upload APK**. The page shows progress, cancellation, available storage, and the received app's package/version. Transfer errors appear beside these controls. Uploads use 1 MB parts with retries and server-confirmed offsets, without a fixed total-size cap. Keep Mortal TV running. Bundles such as `.apks`, `.xapk`, or `.aab` are not supported. One APK can wait at a time; remove it before sending another.

After a successful upload, Android's install prompt opens automatically while Mortal TV is in the foreground. If another screen/app is open, return to Mortal TV and the pending prompt opens then. You can also choose **Show install prompt on TV** on the PC or **Settings → APK from PC** on the TV. If required, enable **Allow from this source** for Mortal TV, return, and choose Install again. Android requires TV confirmation and may reject incompatible APKs or signature/version conflicts; upload success does not mean installation success. Install only APKs from publishers you trust. Mortal TV APKs must match the existing signing certificate and cannot be older than the installed version.

Canceling Android's installer keeps the file in app cache for another attempt. **Delete uploaded APK** on the PC deletes the received file and incomplete transfer after confirmation, without uninstalling any app. TV Settings also offers **APK from PC → Remove APK**. Canceling a transfer discards incomplete data; if the connection fails during cleanup, use Delete uploaded APK before retrying. Android can clear cached files when space is low; upload again if necessary. The manager stops when the app closes, and installation may require additional space beyond the transferred APK. Reload the PC page after an app update to load the current uploader.

Importing under the same source name replaces only that source after successful parsing. Empty/failed refreshes retain the prior catalog. Renaming an existing source is intentionally restricted to protect channel identity; add a new named source and delete the old one if required.

## Quality filtering

Use **Quality: All** above the channel grid to filter by 8K/4320p, 4K/2160p, 1440p, 1080p, 720p, 576p, 512p, 480p, 360p, 240p, or 144p. Additional numeric resolution labels found in your playlist are included automatically. The filter combines with search, group, and source filters, stays selected after playback, and resets when changing destinations. Select **All qualities** to clear it; an empty result also offers a clear action.

Quality comes from channel names or groups: FHD/Full HD maps to 1080p, HD to 720p, and UHD/4K to 2160p. Dimensions such as `1280x720` are recognized. Interlaced labels are grouped by vertical resolution (1080i under 1080p). SD remains separate because its resolution is unspecified; missing/conflicting labels appear as Unknown. These are playlist labels, not measured playback quality. Adaptive-stream renditions remain available through **Tracks / quality** during playback. Official service/browser cards appear only under All qualities because their playback resolution is managed by the service.

## Free movie channels

**Free Movies** links to the official Plex Movies guide, Classic Cinema on Plex, and The Roku Channel. These services play in their official app/browser, carry ads, and have regional restrictions; Roku may be unavailable in Sri Lanka. Imported live channels with a group containing `movie` also appear here and use the built-in player. These links do not provide a subscription to HBO or Warner Bros. premium networks. No authorized free source for Batman v Superman is bundled.

Sources checked November 1, 2026: [Plex Movies](https://watch.plex.tv/live-tv/category/movies), [Classic Cinema](https://watch.plex.tv/live-tv/channel/classic-cinema), [Roku guide](https://www.roku.com/blog/live-tv-channel-guide), and [HBO subscription options](https://www.hbo.com/ways-to-get). Individual channel lineups can change.

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
