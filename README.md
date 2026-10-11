# NovaDial

A real Android default dialer. It uses your phone's own contacts, call log, SIM cards and
voicemail — no sample data, no simulated calls, no cloud. NovaDial does not use the internet.

Package: `com.aistudio.novadial.kpxm` — minSdk 24 — targetSdk 36 — version 2.0.0 (versionCode 3)

> **Rebuilt, not patched.** NovaDial 2.0 uses the mature **Secure Dialer** codebase
> (`com.example`, GPL-3.0-or-later) as its foundation, rebranded to NovaDial, with NovaDial's
> unique features re-added on top. See **[PORTING_PLAN.md](PORTING_PLAN.md)** for the full audit of
> the original issues and how each one is resolved, plus the status of the deferred features
> (Emergency SOS, Analytics).

## Build
1. Open the folder in **Android Studio** and let Gradle sync.
2. Build Variants -> `debug` -> Build > Build APK(s). Install it on a real phone.
3. On the phone: Settings > Apps > Default apps > Phone app > NovaDial. Accept every permission prompt.
4. Release build: set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (or put
   `my-upload-key.jks` in the project folder). Without a keystore the release APK is signed with the
   debug key: it installs for testing, but Google Play rejects it.
5. Unit tests: `./gradlew testDebugUnitTest`

> This project was assembled without a JDK or the Android SDK, so the 2.0 tree has **not been
> compiled or run**. The pure logic (announcer script, protection rules) is covered by unit tests, and
> every integration point was type-checked against the real API surface — but you must build it and
> test on a phone. If Android Studio shows errors, send them back to be fixed.

## Features

### Calling (from the Secure Dialer core)
- One `CallManager` shared across the InCallService, notifications and the floating widget, so the
  call screen always reflects the real call state.
- Mute / speaker / hold / answer reflect Android's confirmed state; the app does not change the
  phone's audio settings by itself.
- Call timer from Android's own connect time; correct call direction on every API level.
- Real SIM names, real default SIM, exact subscription match when choosing a SIM.
- An **audio** button cycles the outputs the phone really offers (earpiece, wired headset,
  Bluetooth, speaker).
- Signal bars and dBm come from Android (nothing estimated); network type says "4G LTE" / "5G".

### Focus Mode & Call Protection (NovaDial feature, re-added)
- A real `CallScreeningService` (`NovaDialCallScreeningService`) screens calls **before they ring**.
- **Focus mode:** Off · Saved contacts only · VIP only · Silence all.
- **Protection level:** Standard · Strict (silence unsaved) · Maximum (only VIP/favorites/repeat).
- **Repeat caller** grace: a second call from the same number within 3 minutes always rings.
- **VIP numbers** always break through. Blocked numbers and offline-spam entries are rejected.
- **Emergency numbers are never silenced or blocked.**
- Silenced calls still appear in the call log as missed calls (Android 10+).

### Caller Announcer (NovaDial feature, re-added)
- Speaks the caller's name while the phone is ringing (TextToSpeech, ringtone audio stream).
- Message presets (Friendly / Formal / Playful) or a custom template with `{name}` `{user}` `{type}`.
- Your name, repeat count (1–3), speech speed, pitch and volume.
- "Headphones only" mode and "announce unknown numbers" toggle, plus a test button.
- Never longer than 15 seconds; stops when you answer or reject; stays quiet in silent mode and
  Do Not Disturb; skipped entirely for calls that Focus mode / protection silenced.

### Everything else
- Contacts, recents, dialpad, favorites, voicemail, speed dial, quick replies.
- Offline spam protection: local blocklist + CSV import (no network, no invented database).
- Call recording to the app's private vault, call notes, callback reminders, fake-call simulator.
- Dynamic Island call capsule, appearance/theming, biometric app lock, encrypted local backup,
  vCard import/export.

## Settings layout
Settings is a multi-tab panel. Tab 0 is the category list; tabs 1–8 are the core categories, and
**tab 9 is "Focus & Protection"** (focus mode, protection level, VIP numbers, caller announcer).

## Privacy
- `INTERNET` permission is explicitly **removed** from the manifest — the app cannot use the network.
- All data (contacts mapping, call history, recordings, notes, backups) stays on the device.
- `allowBackup=false` with explicit exclude rules.

## License and credits

NovaDial is licensed **GPL-3.0-or-later**. The full license text is in [`LICENSE`](LICENSE).

NovaDial is a GPLv3 port built on the **Secure Dialer** codebase
(<https://github.com/Secure-Phone-apps/Secure-Dialer>), **Copyright (C) 2026 MovStore**,
GPL-3.0-or-later. Every file derived from Secure Dialer keeps its original
`Copyright (C) 2026 MovStore` / SPDX header unchanged; the files written for NovaDial are
listed in [`NOTICE.md`](NOTICE.md) and carry a `Copyright (C) 2026 NovaDial contributors`
header. See [`PORTING_PLAN.md`](PORTING_PLAN.md) for the full derivation log.

Third-party components are open-source only (AndroidX, Room, SQLCipher, Coil — Apache-2.0
or MIT). There are no ads, no analytics, no server and no `INTERNET` permission.

### The name "NovaDial"

An unrelated open-source project also uses the name "NovaDial". This app is **not**
affiliated with it. If you distribute builds, keep the `applicationId` distinct (see
below) so the two packages never collide in a store or on a device.

### `reference/`

The `reference/` folder holds archived original NovaDial sources kept for historical
context only. It is **not** part of the Gradle build (it is outside `app/src`) and is
never compiled or shipped.

### Application id

The current id `com.aistudio.novadial.kpxm` was generated by an AI Studio template and
should be replaced (for example `com.novadial.app`) **before the first public release**.
Changing it means: users must uninstall the old build (different package = different app
data), and any hard-coded widget action strings (`com.aistudio.novadial.widget.*`) must be
renamed to match. The id has intentionally **not** been changed yet.

## Phone test checklist
1. Install, set as default phone app, accept permissions.
2. Close NovaDial. Have someone call you: the call screen opens with the caller's name (or the
   notification, if the phone is locked).
3. Answer, then ask the caller to hang up: the call screen must close by itself.
4. During a call, tap mute / speaker / hold / audio: the buttons must match what you hear.
5. Make a call on SIM 2 (if you have two SIMs): it must use SIM 2.
6. Block a number (Settings > Spam & Call Block). Call from it: it must not ring. Repeat with the
   number written two ways (with and without +country code).
7. Settings > Focus & Protection > Focus mode: choose "Saved contacts only", call from an unknown
   number: silent ring, shown as missed.
8. Settings > Focus & Protection > Caller announcer > Test announcement. Then take a real call: the
   name is spoken and stops when you answer.
9. Put the phone on silent: no announcement. Turn on Do Not Disturb: no announcement.
10. Recents > Voicemail (only if your carrier supports visual voicemail): play a message.
11. Recents: call history, notes and recordings open and play.

## License
GPL-3.0-or-later — see [`LICENSE`](LICENSE) and the **License and credits** section above
and [`NOTICE.md`](NOTICE.md).
