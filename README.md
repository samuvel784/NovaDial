# NovaDial

A real Android phone app (default dialer). It uses your phone's own contacts, call log, SIM cards and voicemail.
No sample data, no simulated calls, no cloud. NovaDial does not use the internet.

Package: `com.aistudio.novadial.kpxm` - minSdk 26 - targetSdk 36 - version 1.1.0

## Build
1. Open the folder in **Android Studio** and let Gradle sync.
2. Build Variants -> `debug` -> Build > Build APK(s). Install it on a real phone.
3. On the phone: Settings > Apps > Default apps > Phone app > NovaDial. Accept every permission prompt.
4. Release build: set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (or put `my-upload-key.jks` in the project folder).
   Without a keystore the release APK is signed with the debug key: it installs for testing, but Google Play rejects it.
5. Unit tests: `./gradlew testDebugUnitTest`

> This project was edited without access to Gradle or the Android SDK, so it has **not been compiled or run**.
> The call logic was type-checked against the real Android API and the pure logic (announcer script, protection rules) was run
> with unit tests, but you must build it and test on a phone. If Android Studio shows errors, send them back to be fixed.

## What this version fixes (v1.1.0)

### Calling
- **One `CallManager` for the whole app** (`CallManager.getInstance`). Before, the InCallService, notifications and floating widget created
  new managers that replaced the one the call screen watches, so the call screen stopped updating (it stayed open after the other person hung up,
  and notifications could show an empty caller).
- Mute, speaker, hold and answer are shown **after Android confirms them**. The app no longer changes the phone's audio settings by itself.
- Call timer comes from Android's own connect time. Call direction stays "incoming" after you answer, also before Android 10.
- A call that never starts no longer leaves a dead call screen open.
- Real SIM names, the real default SIM, and the exact subscription match when you pick a SIM.
- New **audio** button: cycles through the outputs the phone really offers (earpiece, wired headset, Bluetooth, speaker).
- Signal bars and dBm are what Android reports (nothing is estimated). Network type says "4G LTE" / "5G", not VoLTE.

### Blocking, Focus mode and protection (they did nothing before)
- The screening service now really **rejects** numbers on your blocklist and spam rules set to Auto-Block, **silences** Silence rules, and applies
  Focus mode and the protection level. Emergency numbers are never touched. Silenced calls show as missed calls (needs Android 10+).
- Blocked numbers match by their last 8 digits, so a number saved as `9876543210` also blocks `+919876543210`.
- Settings > Focus Mode, Protection & Caller Announcer: Focus mode, protection level, repeat callers, VIP numbers.
- Toll-free numbers are no longer labeled "telemarketer / spam".

### Caller announcer
- Uses your settings: message (Friendly / Formal / Playful / Custom with `{name}` `{user}` `{type}`), your name, repeat 1-3, speed, pitch,
  volume, private mode (headphones only), announce unknown numbers, test button.
- Never longer than 15 seconds, stops when you answer / reject, stays quiet in silent mode and Do Not Disturb, and is not spoken for calls that
  Focus mode or protection silenced. Spoken on the ringtone audio stream, never changes the ring volume.

### Removed because it was fake
- **Record button and Recordings Vault** (nothing recorded; Android does not let apps record the other side of a call).
- **In-call video button** (only showed a message).
- **Invented spam database** (about 20 made-up numbers, 12 set to auto-block) and the fake "Refresh threat feed" button.
  Old entries are deleted automatically on first start. Only rules you add yourself remain.
- **Invented "community reports"** (fake report counts) and **three sample SIM rules** that silently routed your +44 calls to SIM 2.
- "Simulate an incoming call" presets: the tab is now **Check a number**, which shows how your own rules treat a number.
- "Cloud Storage (Sync Active)" wording and the voicemail guide that described recording and sending voicemails.
- US-only emergency defaults (911, US poison control). The default is now **112**, which works on every mobile network.
- The emergency "Test Trigger" now asks for confirmation (it starts the real SOS countdown, which you can cancel).

### Voicemail
- Playback is now **real audio** from the voicemail provider (before, only a progress bar moved). Added the `READ_VOICEMAIL` permission.
  Voicemail only appears if your carrier supports visual voicemail.

### Build and privacy
- Release build (R8) has keep rules for Moshi and Room (before, a minified release would likely crash reading data).
- Removed unused Firebase, OkHttp, Retrofit libraries and the `INTERNET` permission.
- Backups: `allowBackup=false` plus explicit exclude rules.
- Release signing falls back to the debug key instead of failing or producing an unsigned APK.

## Still to know
- Contacts "saved" for protection rules means contacts in the phone's address book.
- The floating call widget needs the "Display over other apps" permission, which Google Play reviews closely.
- `SEND_SMS` (reject with message, SOS) is a restricted permission on Google Play and needs a declaration form.
- Call waiting: the call screen shows one call; swap/merge exist in the service but have no buttons yet.
- Calls ended by the other person are not added to NovaDial's own history table; the Recents list reads the system call log.

## Phone test checklist
1. Install, set as default phone app, accept permissions.
2. Close NovaDial. Have someone call you: the call screen opens with the caller's name (or the notification, if the phone is locked).
3. Answer, then ask the caller to hang up: the call screen must close by itself.
4. During a call, tap mute / speaker / hold / audio: the buttons must match what you hear.
5. Make a call on SIM 2 (if you have two SIMs): it must use SIM 2.
6. Block a number (Settings > Spam Shield). Call from it: it must not ring. Check with the saved number written two ways (with and without +country code).
7. Settings > Focus Mode: choose "Saved contacts only", call from an unknown number: silent ring, shown as missed.
8. Settings > Caller announcer > Play a test announcement. Then take a real call: the name is spoken once and stops when you answer.
9. Put the phone on silent: no announcement.
10. Recents > Voicemail (only if your carrier supports it): play a message and hear it.
11. Turn on Do Not Disturb: no announcement.
12. Settings should no longer show Recordings, and the call screen no longer has a record or video button.
