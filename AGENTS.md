# AGENTS.md

## Product goal

Build a fast, polished native Kotlin Android TV application for the Mortal T1 Android box. The app is an IPTV player and a launcher for user-managed web links. Prioritize reliable playback, excellent Bluetooth remote navigation, readable card-based screens, and easy maintenance over decorative animation.

This file governs development throughout this repository. Work toward complete, usable features; do not present placeholders, untested playback, or mock update flows as finished functionality.

## Target device

The owner reports this device configuration:

- Model: Android TV / Mortal T1 Android box.
- Android TV OS version: 16.
- Security patch: January 1, 2025.
- Kernel: 6.1.118.
- OS build: `DR822.R1.1.1.C3/02.v1.1.1.260511`.
- Input: Bluetooth Android TV remote.

Treat these as owner-provided details, and verify actual capabilities on the device when available. Do not infer codec, DRM, storage, or launcher support from the OS version alone.

## Engineering defaults

- Use Kotlin and native Android APIs. Prefer Jetpack Compose for TV, AndroidX Media3/ExoPlayer, coroutines, Flow, ViewModels, Room for structured local data, and DataStore for preferences where appropriate.
- Check current official documentation and compatible stable dependency versions when implementing; do not pin guessed or obsolete versions in this document.
- Keep UI, playlist parsing, persistence, networking, playback, link launching, and update handling separated and independently testable. Avoid unnecessary modules and frameworks.
- Keep network access, parsing, database operations, and large imports off the main thread. Use cancellation, bounded work, sensible timeouts, lazy lists, and cached/resized logos.
- Persist data locally. Basic functionality must not require an account, subscription, paid service, or cloud project kept alive by periodic check-ins.
- Preserve the established application ID and signing identity once the app is distributed.

## TV interface and remote support

- Provide clear destinations for Home, Playlists/Channels, Sri Lankan TV, Favorites, Recently Watched, Others, and Settings. Combine screens where doing so improves simplicity.
- Use attractive channel and link cards, readable text at TV viewing distance, consistent spacing, safe screen margins, and strong contrast.
- Every action must work using D-pad directions, OK/select, Back, and supported media buttons without a touchscreen or mouse. Do not rely on long-press as the only way to access an action.
- Make focus unmistakable. Define predictable focus movement, prevent focus traps, restore focus and scroll position after navigation, and keep focused items visible.
- Make dialogs, player controls, seek bars, settings, import forms, and error states fully remote-operable. Back must dismiss the current overlay before leaving its screen.
- Avoid ornamental animations, autoplaying previews, and unnecessary recomposition. Use immediate feedback and lightweight transitions only when useful.
- Include accessible labels and usable empty, loading, offline, and error states with clear recovery actions.
- Configure TV launcher visibility, banner/icon resources, landscape behavior, and TV input declarations correctly.

## IPTV sources and playlist handling

- Accept direct stream URLs, remote M3U/M3U8 playlist URLs, pasted Extended M3U text, and local playlist file imports.
- Distinguish a channel-list M3U from an HLS master/media manifest. An HLS manifest is a playable stream, not a list of separate TV channels.
- Parse `#EXTM3U`, `#PLAYLIST`, `#EXTINF`, channel display names, `tvg-id`, `tvg-name`, `tvg-logo`, and `group-title`. Preserve useful metadata and support semicolon-separated groups.
- Handle UTF-8/BOM, CRLF/LF, blank lines, comments, missing optional metadata, commas in names, malformed entries, relative URLs, duplicates, and large playlists gracefully.
- Resolve relative URLs against the source playlist location. Preserve signed query parameters and do not rewrite stream URLs destructively.
- Support multiple named sources, add/edit/delete, manual refresh, import summaries, and optional configurable refresh. Keep the last usable cached catalog when refresh fails.
- Use stable channel identities so refreshes retain favorites and history. Do not merge distinct channels solely because their names match.
- Provide search, category/source filters, sorting, favorites, and recently watched channels. Use fallback artwork when logos are absent or broken.
- Treat the owner's large pasted playlist as an example of supported input, not a requirement to hardcode its channels or assume its URLs are available.

## Built-in playback and external players

- Provide a real built-in Media3 player with adaptive streaming where supported by the device and selected Media3 modules.
- Include play/pause, loading feedback, retry, channel information, elapsed/duration or live status, seek bar, 10-second forward/backward, and return-to-live controls where applicable.
- Enable seeking and 10-second skips only when the source exposes a seekable timeline or DVR window. For non-seekable live streams, clearly disable those controls. Do not promise seeking beyond a live window.
- Include audio track selection, subtitle selection and on/off, aspect ratio/fit modes, available quality selection with Auto, volume/mute where platform support allows, and configurable playback preferences.
- Handle audio focus, lifecycle transitions, screen-on during playback, decoder errors, network loss, bounded reconnect attempts, and release of player resources. Avoid multiple unintended active players.
- Support resume for seekable content and sensible live-edge behavior for live content. Offer diagnostics that distinguish unavailable streams, connectivity problems, and unsupported formats without exposing credentials.
- Offer Open with external player using Android intents and a chooser; optionally remember a preferred handler. Handle missing compatible apps and failed launches gracefully.
- Add XMLTV EPG support with channel mapping, local time display, now/next information, and cache/refresh behavior as a follow-on feature after reliable core playback.
- Do not imply universal codec or DRM compatibility. Report device/source limitations honestly.

## Add and manage links from a PC

- Default to a free local-network management page served by the TV app. Show its LAN address, a pairing code, and optionally a QR code in the TV UI. A PC on the same network must be able to manage sources and links through a browser.
- Support playlist URL submission, direct stream submission, pasted M3U text, file upload, and Others link management. Reflect changes in the TV UI without restarting the app.
- Explain that the TV app must be running and reachable; handle Wi-Fi changes and show the current address.
- Require pairing/authentication, expire sessions, validate requests and upload sizes, and provide an explicit enable/disable control. Do not expose an unauthenticated server or configure public port forwarding.
- Protect authenticated state-changing requests against cross-site request forgery. Do not claim that an HTTP LAN page encrypts credentials; document the local-network trust assumption and avoid logging sensitive inputs.
- Include local import/export and backup/restore so the owner is not dependent on the LAN server. Make any cloud sync optional and avoid services requiring inactivity check-ins.

## Others: ordinary web links

- Provide a separate section named **Others** for ordinary links, distinct from IPTV streams and playlists.
- Allow create, edit, delete, rename, reorder, optional icon, and optional grouping/search of link cards from the TV and PC management page.
- Selecting a card opens its URL in the default browser. Also provide an Open with action using an appropriate chooser.
- Offer Add to Home for a shortcut on this app's Home screen. Offer an Android launcher shortcut only if the installed TV launcher supports it, and explain the limitation otherwise.
- Validate allowed URI schemes, use safe defaults for HTTP/HTTPS, handle missing browsers, and do not execute arbitrary intent or script schemes supplied in imported data.

## Built-in Sri Lankan TV catalog

- Provide a curated Sri Lankan live TV section with channel names, logos, categories, and built-in/external playback options.
- Research current public sources during implementation, preferring official broadcaster streams or sources with clear redistribution permission. Record provenance and verification dates.
- Aim for broad available coverage; never claim every Sri Lankan channel is playable simply because a link exists online. Explain unavailable, regional, expired, or unsupported streams accurately.
- Keep the catalog refreshable independently of an APK release, with a bundled fallback and the last working cached catalog. A static versioned catalog hosted alongside the repository is suitable when available.
- Allow the owner to add or override channels. Do not ship credentials, bypass access controls, or treat unofficial examples as verified sources.

## GitHub builds, releases, and in-app updates

- Set up GitHub Actions so pushes to `main` run appropriate checks, build the APK, and publish a versioned GitHub Release with a downloadable APK and update metadata after checks pass.
- Use reproducible documented build commands and a consistent, strictly increasing Android `versionCode`; use a readable `versionName`. Ensure concurrent runs cannot publish conflicting versions or replace a newer update with an older one.
- Release updates must use the same application ID and signing certificate as the installed app. Keep the keystore, passwords, and tokens in GitHub Actions secrets or an appropriate local secret store; never commit them.
- Give workflows only the permissions required. Publish metadata only after the corresponding APK is available. Include version name/code, release notes, APK URL, checksum, and compatibility information.
- Provide an in-app Update screen showing installed/latest versions, check/download actions, progress, cancellation/retry, release notes, and an Install update action.
- Download over HTTPS, verify integrity and the expected package/signing identity before installation, and preserve user data during updates. A checksum alone does not establish publisher identity.
- Use Android's supported package installer flow and securely grant access to the APK. Explain and handle the install-from-this-source permission when required.
- Automate checking and downloading where configured, then invoke Android's installer. Normal Android installations require system/user approval; do not promise silent self-updates without appropriate managed-device privileges.
- Keep private repository access tokens out of the APK. Choose a public release endpoint or a secure alternative appropriate to the actual repository visibility.
- At the end of every code-changing delivery, produce a working APK with correct versioning, report its path and checks, and push/publish through the configured GitHub workflow when repository access and credentials are available and authorized. Do not create empty releases for documentation-only edits.
- If the SDK, signing material, remote repository, credentials, network, or device access is unavailable, state the exact blocker and provide the concrete remaining step. Never claim a build, push, release, or device test succeeded without evidence.

## Reliability and data protection

- Include persistent settings, favorite/history management, clear cache, source refresh status, backup/export, and recoverable import failures. Provide confirmation for destructive user-data actions.
- Store only needed data and request minimal Android permissions. Redact tokens, credentials, and sensitive URL query parameters from logs and exported diagnostics.
- Support HTTP sources when needed for user-provided IPTV, with an intentional documented network security policy; never disable TLS certificate verification.
- Limit playlist/download sizes and redirects, handle errors without crashing, and keep untrusted source content separate from executable code.
- Keep the app usable when a source, logo host, catalog endpoint, or update endpoint is unavailable.
- Avoid ads, analytics, accounts, recording, catch-up, or other major scope additions unless requested. Add small usability features when they support the owner's stated goals.

## Verification and delivery

- Prioritize delivery in this order: TV shell/focus and persistence; source import and playback; PC management and Others; Sri Lankan catalog; signed release/update flow; polish and EPG.
- Verify parser edge cases, source refresh persistence, import/export, update version comparisons and rejection paths, and other substantive logic with focused tests.
- Run the project's relevant compilation, unit tests, lint, and release packaging checks for changed code. Keep a clean checkout build documented.
- Exercise remote-only navigation, large playlists, live versus seekable playback, external intents, network failure recovery, and update installation on a TV emulator or device as available.
- Test upgrade over a previous signed version and verify that playlists, Others links, favorites, and settings survive.
- Distinguish automated checks, emulator checks, and actual Mortal T1 device checks. Device-specific behavior remains unverified until tested on that hardware.
- Keep README setup, PC pairing, source import, playback limitations, release secrets, update installation, and recovery instructions current.
- In each delivery report what changed, what was verified, the APK/release location when applicable, and remaining blockers. Be precise; do not promise flawless operation without supporting tests.
