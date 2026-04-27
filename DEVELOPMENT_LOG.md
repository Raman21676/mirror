# Mirror Project — Development Log

> **Session Date**: 2026-04-27
> **Agent**: Kimi Code CLI
> **Git Commit After Session**: `c89355c`

---

## Session Start: Project Exploration

**User Request**: "Go through the mirror project on my desktop, understand the whole architecture, read all markdown files, and be ready for my next command."

### Actions Taken
1. Listed `~/Desktop/mirror/` directory structure
2. Read `AGENTS.md`, `README.md`, `LICENSE`
3. Discovered no `PROJECT_TODO.md` existed (referenced in `AGENTS.md` but missing)
4. Launched `explore` subagent to thoroughly read ALL source files across:
   - `mirror-core/` (Rust)
   - `mirror-target/` (Kotlin + Android)
   - `mirror-host/` (Kotlin + Android)
   - `mirror-signaling/` (Rust server skeleton)

### Exploration Results (Summary)
- **Two-app architecture**: Target (CCTV server) and Host (viewer client)
- **Tech stack**: Kotlin + Rust NDK, WebRTC fallback, AES-256-GCM encryption
- **Pipeline status**: Camera, encoder, TCP server, decoder all had functional code BUT not fully wired together
- **Rust core**: Had real crypto (`ring`) and mux/demux, but also had stub video/audio encoder code
- **Major gap**: Host MainActivity used broken fragment navigation (`findNavController` with missing `NavHostFragment`)
- **Security gap**: Hardcoded encryption key `ByteArray(32) { 0x42 }`
- **Build gap**: `cargo-ndk` not installed, Rust targets present but cross-compilation tool missing

---

## Previous Session Context (Provided by User)

User pasted extensive terminal history showing previous Claude AI sessions had:
1. Fixed Host app navigation (converted from broken fragment nav to activity-based BottomNavigationView)
2. Stripped GPS completely from Target app (permissions, manifest, service, dependencies)
3. Installed `cargo-ndk`
4. Fixed Rust core compilation errors (`ring` 0.17 API changes: `Aes256Gcm` → `AES_256_GCM`, `rand::fill` → `rand::thread_rng().fill()`)
5. Fixed `build.rs` (removed forbidden `cargo:rustc-cfg=target_arch=...` lines)
6. Disabled broken `opus` feature in `Cargo.toml`
7. Removed CMake references from Target `build.gradle.kts` (CMakeLists.txt didn't exist)
8. Built Rust .so for Android (arm64-v8a, armeabi-v7a)
9. **Task 1**: Rewrote JNI bridge to expose 4 real functions (encrypt, decrypt, mux, demux)
10. Deleted stub `video/` and `audio/` encoder code from Rust
11. Added `CryptoSession::from_raw_key()` for symmetric 32-byte key encryption
12. Updated both Kotlin `RustBridge.kt` files
13. Committed and pushed to GitHub

**Key Design Decision from Previous AI**: Do NOT implement H.264 in Rust. Use Android MediaCodec for video/audio encoding/decoding. Rust's job is only encryption + packet framing + JNI bridge. Use plain TCP over local WiFi (no backend server).

---

## Current Session: Building Stable MVP

### Step 1: Deep Code Analysis

**Read critical pipeline files**:
- `MirrorTargetService.kt` — Found complete pipeline: Camera → Encoder → `nativeMuxPacket` → `nativeEncryptPacket` → `TransportManager.send()`
- `TcpServerManager.kt` — TCP server on port 8080, length-prefixed `[4-byte len][data]`, one client at a time
- `TransportManager.kt` — Routes TCP vs WebRTC, auto-sends codec config to new clients
- `MediaCodecEncoder.kt` — H.264 hardware encoder, outputs AnnexB format, stores SPS/PPS in `codecConfig`
- `CameraCaptureManager.kt` — Camera2, 640×480, YUV_420_888 → NV21 conversion
- `AudioCaptureManager.kt` — AudioRecord 44.1kHz mono 16-bit PCM
- `LiveCameraActivity.kt` — Host receiver: decrypt → demux → route by type byte
- `TcpClientManager.kt` — Host TCP client, auto-reconnect every 3s
- `TransportManager.kt` (Host) — TCP first, then WebRTC fallback
- `MediaCodecDecoder.kt` — H.264 decoder, detects SPS/PPS NAL units (types 7 & 8)
- `AudioPlayer.kt` — AudioTrack stream mode for PCM playback
- Rust `mux.rs`, `demux.rs`, `jni_bridge/mod.rs` — Verified format compatibility

**Critical Discovery**: The Rust JNI bridge's `nativeDemuxPacket()` ALREADY prepends the stream type byte to each payload (line 153-158 in `jni_bridge/mod.rs`). This matches exactly what Host's `LiveCameraActivity` expects when it reads `payloadWithType[0]` as the type byte. The pipeline is internally consistent.

### Step 2: Identified Bugs to Fix

1. **Host app missing Rust .so files** — `mirror-host/app/src/main/jniLibs/` did not exist. Host app loads `mirror_core` but had no native library.
2. **Host `TcpClientManager.send()` missing length prefix** — Target expects `[4-byte len][data]`, but `send()` wrote raw bytes. Would break test packets and any future control messages.
3. **Redundant TCP client in Host `MainActivity`** — MainActivity created its own `TcpClientManager` that connected independently. LiveCameraActivity created ANOTHER `TransportManager` with its own TCP client. Two connections fighting for the same Target server.
4. **No SettingsActivity** — Bottom nav "Settings" item did nothing.
5. **Target doesn't show its own IP** — User had to guess or use system settings to find the old phone's IP.
6. **No connection status in LiveCameraActivity** — Black screen while connecting; user can't tell if it's working.
7. **Rust demux buffer never clears** — `GLOBAL_DEMUX` is static. If a connection drops mid-packet, stale bytes corrupt the next connection's first packets.

### Step 3: Fixed Rust Core — Added `nativeClearDemux()`

**Problem**: Global `StreamDemux` buffer retains partial packets across reconnections.
**Solution**:
1. Added `pub fn clear(&mut self)` to `StreamDemux` in `demux.rs`
2. Added `Java_com_mirror_core_RustBridge_nativeClearDemux()` in `jni_bridge/mod.rs`
3. Updated both Kotlin `RustBridge.kt` files to declare `external fun nativeClearDemux()`
4. Rebuilt Rust for Android: `cargo ndk -t arm64-v8a -t armeabi-v7a ... build --release`

**Build Error Encountered**: `error[E0616]: field 'buffer' of struct 'StreamDemux' is private`
- **Cause**: JNI bridge tried `demux.buffer.clear()` directly
- **Fix**: Changed to `demux.clear()` after adding the public method

### Step 4: Copied Rust .so to Both Apps

```bash
# Target already had .so from cargo-ndk output
cp -r mirror-target/app/src/main/jniLibs/* mirror-host/app/src/main/jniLibs/
```

Verified both APKs contain `libmirror_core.so` for both architectures.

### Step 5: Fixed Host `TcpClientManager.send()`

**Before** (broken — no length prefix):
```kotlin
sock.getOutputStream().write(data)
```

**After** (fixed — matches Target receive protocol):
```kotlin
val lengthHeader = ByteArray(4).apply {
    this[0] = (data.size shr 24).toByte()
    this[1] = (data.size shr 16).toByte()
    this[2] = (data.size shr 8).toByte()
    this[3] = data.size.toByte()
}
output.write(lengthHeader)
output.write(data)
```

### Step 6: Rewrote Host `MainActivity.kt`

**Removed**:
- `TcpClientManager` field and all connection/disconnection logic
- `updateConnectionUI()` method
- Redundant connect/disconnect/test button handlers that managed their own TCP connection

**Kept**:
- IP input field
- "Connect" button → now validates IP and launches `LiveCameraActivity` directly
- "Test" button → repurposed to also launch LiveCameraActivity
- Bottom navigation with all 5 items properly wired

**New**: `SettingsActivity` reference in bottom nav (previously did nothing).

### Step 7: Created Host `SettingsActivity`

- Created `mirror-host/app/src/main/java/com/mirror/host/settings/SettingsActivity.kt`
- Created `mirror-host/app/src/main/res/layout/activity_settings.xml`
- Added `SettingsActivity` declaration to Host `AndroidManifest.xml`
- Removed unused `ACCESS_FINE_LOCATION` permission from Host manifest

### Step 8: Updated Target Layout + MainActivity to Show IP

**Layout change** (`activity_main.xml`):
- Added `TextView` (`@+id/ip_text`) above buttons to display WiFi IP

**Code change** (`MainActivity.kt`):
- Added `getWifiIpAddress()` using `WifiManager.connectionInfo.ipAddress`
- Displays IP in format `IP: 192.168.x.x` on startup
- **Deprecation warning**: `WifiManager.connectionInfo` and `.ipAddress` are deprecated in API 31+. Still functional but flagged by compiler. Acceptable for MVP.

### Step 9: Rewrote Host `LiveCameraActivity.kt`

**Major improvements**:
1. Uses XML layout (`activity_live_camera.xml`) instead of programmatic `SurfaceView` only
2. Added `FrameLayout` container for dynamic `SurfaceView` creation
3. Added `connection_status_overlay` TextView — shows:
   - "Connecting via TCP..."
   - "Connecting via WebRTC..."
   - "Disconnected"
   - "Connection failed"
   - Hidden when connected
4. Added `connection_info_overlay` TextView — shows "Connected to 192.168.x.x"
5. Calls `RustBridge.nativeClearDemux()` before connecting
6. SurfaceView and decoder created ONLY after TCP connection succeeds (prevents decoder errors from missing SPS/PPS)
7. Proper cleanup in `onDestroy()`

**Layout file issue encountered**: `android:padding="8dp 16dp"` is invalid in Android XML (CSS-like shorthand not supported).
- **Fix**: Changed to `android:paddingHorizontal="16dp"` + `android:paddingVertical="8dp"`

### Step 10: Build Verification

**Host app build**:
```bash
export JAVA_HOME="/Users/kalikali/Library/Java/JavaVirtualMachines/jdk-21.0.6+7/Contents/Home"
cd mirror-host && ./gradlew :app:assembleDebug
```
- **First attempt FAILED**: Gradle 8.4 incompatible with system default Java 25
  - Error message was just cryptic: `25.0.2`
  - **Fix**: Explicitly set `JAVA_HOME` to Java 21
- **Second attempt FAILED**: `activity_live_camera.xml:26: error: '8dp 16dp' is incompatible with attribute padding`
  - **Fix**: Split into `paddingHorizontal` / `paddingVertical`
- **Third attempt**: `BUILD SUCCESSFUL`

**Target app build**:
```bash
export JAVA_HOME="...jdk-21.0.6+7..."
cd mirror-target && ./gradlew :app:assembleDebug
```
- `BUILD SUCCESSFUL` on first try
- Two deprecation warnings for `WifiManager.connectionInfo.ipAddress` (expected, non-blocking)

**APK sizes**:
- Mirror-Host.apk: 60 MB (includes WebRTC, Maps, ExoPlayer libraries)
- Mirror-Target.apk: 38 MB

### Step 11: Commit & Push

**Created documentation**:
- `PROJECT_TODO.md` — Master plan with phases, file references, build commands
- `DEVELOPMENT_LOG.md` — This file (chronological history)

**Git commit**: `c89355c`
- 19 files changed, 266 insertions, 211 deletions
- Includes built APKs in `mirror-final/`

**GitHub push warning**: `Mirror-Host.apk is 60.30 MB; larger than GitHub's recommended maximum file size of 50.00 MB`. Push succeeded anyway. For future, consider adding `mirror-final/*.apk` to `.gitignore` and distributing APKs via GitHub Releases instead.

---

## Problems Encountered & Solutions Summary

| # | Problem | Root Cause | Solution |
|---|---------|-----------|----------|
| 1 | Gradle fails with cryptic `25.0.2` | System default Java 25 incompatible with Gradle 8.4 | `export JAVA_HOME` to Java 21 before every gradle command |
| 2 | `padding="8dp 16dp"` invalid | Android XML doesn't support CSS padding shorthand | Split into `paddingHorizontal` + `paddingVertical` |
| 3 | `demux.buffer` private field | Added `clear()` method but tried to access private buffer from JNI bridge | Call `demux.clear()` instead of `demux.buffer.clear()` |
| 4 | Host app crashes loading `mirror_core` | No `.so` files in `mirror-host/app/src/main/jniLibs/` | Copy `.so` from Target jniLibs after every Rust rebuild |
| 5 | Test packets fail | `TcpClientManager.send()` didn't use length prefix | Rewrite `send()` to prepend 4-byte big-endian length header |
| 6 | Connection fighting | MainActivity and LiveCameraActivity each had separate TCP clients | Remove TCP client from MainActivity; connection only in LiveCameraActivity |
| 7 | Stale demux buffer | `GLOBAL_DEMUX` static buffer never cleared between sessions | Added `nativeClearDemux()` JNI function |

---

## Decisions Made This Session

1. **No backend server** — TCP over same WiFi only. Zero cost, zero cloud dependency.
2. **Keep WebRTC skeleton** — Don't remove existing WebRTC code, but don't prioritize it either. TCP is the stable path.
3. **Keep hardcoded key for MVP** — Security upgrade deferred to Phase 3. MVP must work first.
4. **APKs in repo** — Committed built APKs to `mirror-final/` for easy access despite GitHub size warning.
5. **No Opus compression** — Raw PCM over TCP. Simpler, no CMake dependency issues, acceptable bandwidth for local WiFi.

---

## Next Session Starting Point

When a fresh AI agent continues:

1. **Read `AGENTS.md`** for project conventions
2. **Read `PROJECT_TODO.md`** for what's done and what's next
3. **Read `DEVELOPMENT_LOG.md`** (this file) for full history
4. **Current stable state**: Both apps compile, APKs built, TCP pipeline wired end-to-end
5. **Recommended next task**: Test on real devices. If it works, move to Phase 3 (secure key exchange). If video doesn't display, debug:
   - Is Target IP correct?
   - Are both on same WiFi?
   - Check logcat for decoder errors (missing SPS/PPS is most likely)
   - Verify port 8080 not blocked by Android firewall
