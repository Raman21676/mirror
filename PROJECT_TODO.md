# Mirror Project — Master TODO & Implementation Plan

> **Last Updated**: 2026-04-27
> **Current Phase**: Phase 2 (Core Pipeline Complete — Stable MVP Ready for Testing)
> **Git Commit**: `c89355c` on `main`

---

## Project Overview

Mirror is an Android surveillance system with **zero backend, zero cost**. Two apps communicate via encrypted TCP over local WiFi.

| App | Package | Role | Install On |
|-----|---------|------|------------|
| **Mirror Me** | `com.mirror.target` | Camera/audio capture + stream server | Old phone (CCTV) |
| **Mirror** | `com.mirror.host` | Receive, decrypt, decode, display | Your main phone |
| **mirror-core** | Rust `cdylib` | AES-256-GCM encryption + stream mux/demux | Shared native lib |

**Architecture**: Kotlin Android apps + Rust NDK library. TCP over local WiFi. No cloud, no paid services.

---

## Phase 1: Environment Setup ✅ COMPLETE

- [x] Rust installed (`rustc`, `cargo`)
- [x] Android NDK installed (`25.2.9519653`)
- [x] `cargo-ndk` installed for cross-compilation
- [x] Android targets: `aarch64-linux-android`, `armv7-linux-androideabi`
- [x] Gradle wrapper in both `mirror-host/` and `mirror-target/`
- [x] `.gitignore` ignores `**/target/`, `build/`, `.gradle/`

**Build Commands**:
```bash
# Rust core for Android
cd mirror-core
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/25.2.9519653"
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../mirror-target/app/src/main/jniLibs build --release
# Then copy .so files to mirror-host jniLibs too
cp -r ../mirror-target/app/src/main/jniLibs/* ../mirror-host/app/src/main/jniLibs/

# Host app
cd mirror-host && ./gradlew :app:assembleDebug

# Target app
cd mirror-target && ./gradlew :app:assembleDebug
```

> **Note**: Must use `JAVA_HOME` pointing to Java 21 (not Java 25) on macOS:
> `export JAVA_HOME="/Users/kalikali/Library/Java/JavaVirtualMachines/jdk-21.0.6+7/Contents/Home"`

---

## Phase 2: Core Streaming Pipeline ✅ COMPLETE

### Rust Core (`mirror-core/`)

| Feature | Status | Notes |
|---------|--------|-------|
| AES-256-GCM encrypt/decrypt | ✅ | Via `ring` crate. JNI: `nativeEncryptPacket()`, `nativeDecryptPacket()` |
| Stream mux/demux | ✅ | Format: `[type(1)][timestamp(4)][len(2)][payload(N)][auth_tag(16)]` |
| `nativeClearDemux()` | ✅ | Resets global demux buffer on new connection |
| JNI bridge | ✅ | 6 functions exposed to Kotlin `com.mirror.core.RustBridge` |
| Video/audio encoding in Rust | ❌ REMOVED | Intentionally deleted. Android MediaCodec handles H.264 |
| Opus audio codec | ❌ REMOVED | Feature disabled, dependency removed. Raw PCM used instead |

**Rust JNI Functions**:
```rust
nativeInit()                          // Init Android logger
nativeGetVersion() -> String          // Cargo package version
nativeEncryptPacket(data, key) -> ByteArray?   // AES-256-GCM; key must be 32 bytes
nativeDecryptPacket(data, key) -> ByteArray?   // Inverse of encrypt
nativeMuxPacket(type, payload) -> ByteArray?   // type: 0x01=Video, 0x02=Audio
nativeDemuxPacket(data) -> Array<ByteArray>?   // Returns [type_byte + payload] for each packet
nativeClearDemux()                    // Clear internal demux buffer
```

### Target App (`mirror-target/`)

| Feature | Status | Notes |
|---------|--------|-------|
| Camera2 capture | ✅ | Back camera, 640×480, YUV_420_888 → NV21 |
| AudioRecord capture | ✅ | 44.1kHz, mono, 16-bit PCM |
| MediaCodec H.264 encoder | ✅ | Hardware accelerated, NV21→NV12, 1Mbps, 15fps |
| Foreground service | ✅ | `camera\|microphone\|mediaProjection` type, wake lock |
| TCP server (port 8080) | ✅ | Length-prefixed protocol `[4-byte len][data]`. One client at a time. |
| Auto-send codec config | ✅ | SPS/PPS resent to every new TCP client |
| WebRTC DataChannel | ✅ | Skeleton exists but NOT primary transport |
| QR signaling (generate) | ✅ | ZXing, chunked for large data |
| Boot receiver | ✅ | Auto-starts service on `BOOT_COMPLETED` |
| GPS/Location | ❌ STRIPPED | Completely removed per user request |
| Screen mirroring | ❌ NOT IMPLEMENTED | MediaProjection declared but unused |
| Display IP on main screen | ✅ | Shows WiFi IP so user knows what to type |

**Data Flow (Target)**:
```
Camera2 → NV21 frame → MediaCodec encoder → H.264 chunk
                                          ↓
AudioRecord → PCM bytes ──────────────────┘
                                          ↓
                              RustBridge.nativeMuxPacket(type, payload)
                                          ↓
                              RustBridge.nativeEncryptPacket(muxed, KEY)
                                          ↓
                              TransportManager.send(encrypted)
                                          ↓
                              TcpServerManager → [4-byte len][encrypted data]
```

**Hardcoded Key** (both apps):
```kotlin
val ENCRYPTION_KEY = ByteArray(32) { 0x42 }  // TODO: Implement secure key exchange
```

### Host App (`mirror-host/`)

| Feature | Status | Notes |
|---------|--------|-------|
| TCP client | ✅ | Connects to Target IP:8080, auto-reconnect every 3s |
| H.264 decoder | ✅ | MediaCodec, renders to SurfaceView |
| Audio playback | ✅ | AudioTrack, 44.1kHz mono 16-bit PCM |
| Decrypt → Demux → Route | ✅ | Unified pipeline handles both video and audio |
| Connection status overlay | ✅ | Shows "Connecting...", "Connected", "Disconnected" |
| Bottom navigation | ✅ | Devices, Live, Map, Gallery, Settings (all wired) |
| SettingsActivity | ✅ | Real activity (skeleton content) |
| WebRTC client | ✅ | Skeleton exists but NOT primary transport |
| Device pairing UI | ⚠️ Skeleton | `DevicePairingActivity` exists but no logic |
| Map tracker | ⚠️ Skeleton | Google Maps with default marker at 0,0 |
| Gallery browser | ⚠️ Skeleton | Empty shell |
| Audio monitor UI | ⚠️ Skeleton | Empty shell |
| Screen mirror | ⚠️ Skeleton | Empty shell |

**Data Flow (Host)**:
```
TcpClientManager → read [4-byte len][data]
                          ↓
                   onDataReceived(encryptedData)
                          ↓
                   RustBridge.nativeDecryptPacket(data, KEY)
                          ↓
                   RustBridge.nativeDemuxPacket(decrypted)
                          ↓
                   For each packet:
                     type=0x01 → MediaCodecDecoder → SurfaceView
                     type=0x02 → AudioTrack.play()
```

**Critical Fix Applied**: `TcpClientManager.send()` now prefixes with 4-byte big-endian length. Previously sent raw bytes causing Target's receiver to misparse.

---

## Phase 3: Security & Key Exchange ⏳ PENDING

- [ ] Replace hardcoded `0x42` key with X25519 key exchange
- [ ] Exchange public keys via QR code scan between devices
- [ ] Derive shared secret with HKDF-SHA256 (already in Rust `CryptoSession::from_shared_secret()`)
- [ ] Remove 16-byte zero auth_tag placeholder in mux format (currently harmless)

---

## Phase 4: Cross-Network (WebRTC) ⏳ PENDING

- [ ] Complete WebRTC signaling server (`mirror-signaling/`) OR
- [ ] Use QR-based SDP/ICE exchange without server (both apps already have QR chunking)
- [ ] Test STUN connectivity with public STUN servers (Google's: `stun.l.google.com:19302`)
- [ ] Make WebRTC fallback reliable when TCP fails

---

## Phase 5: Recording & Gallery ⏳ PENDING

- [ ] Record incoming video stream to MP4 on Host
- [ ] Snapshot (save current frame as JPEG)
- [ ] Gallery browser with thumbnails
- [ ] Download/share recorded files

---

## Phase 6: Polish & Settings ⏳ PENDING

- [ ] Resolution/bitrate selection in Settings
- [ ] Audio on/off toggle
- [ ] Dark theme
- [ ] Battery optimization reminders
- [ ] Multiple device support (device list instead of single IP)

---

## Known Issues & Decisions

| Issue | Decision | Reason |
|-------|----------|--------|
| Java 25 breaks Gradle 8.4 | Use Java 21 | `export JAVA_HOME="...jdk-21.0.6+7..."` |
| CMake referenced but missing | Removed CMake block from Target build.gradle | Rust .so built via cargo-ndk, no CMake needed |
| opus crate fails to build | Disabled `audio-opus` feature | CMake version incompatibility on macOS |
| H.264 in Rust? | NO — use Android MediaCodec | Too complex to implement encoder in Rust |
| GPS tracking? | NO — stripped entirely | User explicitly requested to skip |
| Backend server? | NO — same WiFi only | User wants zero cost, no servers |
| APKs in git? | Yes for now in `mirror-final/` | GitHub warns about 60MB file but accepts it |

---

## File Reference Map

| File | Purpose |
|------|---------|
| `mirror-core/src/lib.rs` | Rust library root |
| `mirror-core/src/crypto/session.rs` | `CryptoSession` with `from_raw_key()` and `from_shared_secret()` |
| `mirror-core/src/network/mux.rs` | `StreamMux::mux()` — packet framing |
| `mirror-core/src/network/demux.rs` | `StreamDemux::feed()`, `demux()`, `clear()` |
| `mirror-core/src/jni_bridge/mod.rs` | All JNI exports |
| `mirror-target/app/src/main/.../service/MirrorTargetService.kt` | Foreground service, wires camera→encoder→Rust→TCP |
| `mirror-target/app/src/main/.../camera/CameraCaptureManager.kt` | Camera2 API, NV21 output |
| `mirror-target/app/src/main/.../encoder/MediaCodecEncoder.kt` | H.264 hardware encoder |
| `mirror-target/app/src/main/.../audio/AudioCaptureManager.kt` | AudioRecord PCM capture |
| `mirror-target/app/src/main/.../network/TcpServerManager.kt` | TCP server, length-prefixed protocol |
| `mirror-host/app/src/main/.../live/LiveCameraActivity.kt` | Receives stream, decrypts, decodes, displays |
| `mirror-host/app/src/main/.../decoder/MediaCodecDecoder.kt` | H.264 hardware decoder |
| `mirror-host/app/src/main/.../audio/AudioPlayer.kt` | AudioTrack PCM playback |
| `mirror-host/app/src/main/.../network/TcpClientManager.kt` | TCP client, auto-reconnect |
| `mirror-host/app/src/main/.../MainActivity.kt` | Dashboard with IP input + bottom nav |
| `AGENTS.md` | Agent context and conventions |
| `DEVELOPMENT_LOG.md` | Full chronological history of all work sessions |

---

## How to Continue Development

1. **Read `DEVELOPMENT_LOG.md`** for full session history
2. **Check this file** for current phase and what's next
3. **Build Rust first**: `cargo ndk ...` then copy `.so` to both `jniLibs/`
4. **Build Android**: `./gradlew :app:assembleDebug` in each app dir
5. **Always use Java 21**, not system default Java 25
