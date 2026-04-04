# Mirror App - Agent Context

## Project Overview

**Mirror** is an Android surveillance app that transforms old phones into CCTV/GPS trackers. It consists of two apps that communicate via encrypted P2P connection.

### Two-App Architecture

| App | Purpose | Runs On |
|-----|---------|---------|
| **Mirror Target** | Captures camera, audio, GPS, screen; streams data | Old phone (CCTV server) |
| **Mirror Host** | Receives streams, displays live view, records | User's main phone |

## Technology Stack

### Core Technologies
- **Languages**: Rust (NDK) + Kotlin
- **Video Encoding**: H.264 via MediaCodec
- **Audio Encoding**: Opus codec
- **Networking**: WebRTC P2P with STUN/TURN fallback
- **Encryption**: AES-256-GCM with X25519 key exchange
- **Maps**: Google Maps SDK

### Why Rust + Kotlin?
- Rust handles performance-critical: encoding, encryption, stream mux
- Kotlin handles Android-specific: UI, permissions, lifecycle, services
- JNI bridge connects them

## Project Structure

```
mirror/
├── mirror-target/         # Target app (CCTV Server)
│   ├── app/src/main/java/com/mirror/target/
│   │   ├── MainActivity.kt
│   │   ├── service/MirrorTargetService.kt    # Foreground service
│   │   ├── camera/CameraCaptureManager.kt    # Camera2 API
│   │   ├── audio/AudioCaptureManager.kt      # AudioRecord
│   │   ├── location/LocationTracker.kt       # Fused Location
│   │   └── core/RustBridge.kt                # JNI interface
│   └── build.gradle.kts
│
├── mirror-host/           # Host app (Remote Client)
│   ├── app/src/main/java/com/mirror/host/
│   │   ├── MainActivity.kt
│   │   ├── live/LiveCameraActivity.kt
│   │   ├── audio/AudioMonitorActivity.kt
│   │   ├── map/MapTrackerActivity.kt
│   │   └── core/RustBridge.kt
│   └── build.gradle.kts
│
├── mirror-core/           # Shared Rust library
│   └── src/
│       ├── video/         # H.264 encoding
│       ├── audio/         # Opus encoding
│       ├── crypto/        # AES-256-GCM encryption
│       ├── network/       # Stream mux/demux
│       └── jni_bridge/    # JNI exports
│
└── mirror-signaling/      # Optional WebSocket signaling server
```

## Key Android Constraints

### Screen-Off Camera (Critical)
- Android 10+ requires `foregroundServiceType="camera"`
- Must start foreground service while app is visible
- Use `PARTIAL_WAKE_LOCK` to keep CPU alive without display
- **Risk**: Some OEMs aggressively kill background services

### Permissions Required
- Camera, Microphone, Location (Fine + Background)
- Foreground Service types: camera, microphone, location
- Battery optimization exemption

### MediaProjection for Screen Mirroring
- Requires system dialog approval (cannot be hidden)
- User must explicitly grant permission each time

## Build Commands

```bash
# Build Rust core for Android
cd mirror-core
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../mirror-target/app/src/main/jniLibs build --release

# Build Target app
cd mirror-target
./gradlew assembleDebug

# Build Host app
cd mirror-host
./gradlew assembleDebug
```

## Implementation Phases

See `PROJECT_TODO.md` for detailed phases:
1. Phase 1: Research & Environment (1-2 weeks)
2. Phase 2: Target App Core (3-4 weeks)
3. Phase 3: Rust Core Library (3-4 weeks)
4. Phase 4: Host App (3-4 weeks)
5. Phase 5: Advanced Features (2-3 weeks)
6. Phase 6: Testing (2 weeks)
7. Phase 7: Deployment (1-2 weeks)

## Current Status

- [x] Project structure created
- [x] Rust core skeleton
- [x] Target app skeleton
- [x] Host app skeleton
- [ ] Camera implementation
- [ ] Audio implementation
- [ ] Location tracking
- [ ] WebRTC integration
- [ ] Encryption layer

## Important Notes for Agents

1. **Always check PROJECT_TODO.md** for the current phase and next tasks
2. **Security first**: Use established crypto libraries (ring), don't roll your own
3. **Battery matters**: Target app should use <10% battery/hour
4. **OEM compatibility**: Test on multiple manufacturers (Samsung, Xiaomi, etc.)
5. **Legal compliance**: This is for legitimate surveillance (home security, anti-theft), not spyware

## Quick Reference

| File | Purpose |
|------|---------|
| `PROJECT_TODO.md` | Full implementation plan with phases |
| `mirror-core/Cargo.toml` | Rust dependencies |
| `mirror-target/app/build.gradle.kts` | Target app build config |
| `mirror-host/app/build.gradle.kts` | Host app build config |
| `mirror-target/app/src/main/AndroidManifest.xml` | Target permissions |
| `mirror-host/app/src/main/AndroidManifest.xml` | Host permissions |
