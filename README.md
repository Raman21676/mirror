# Mirror - Android Surveillance App

Transform your old Android phone into a CCTV camera, GPS tracker, and remote monitoring device.

## Overview

**Mirror** consists of two Android apps:

- **Mirror Target** - Runs on the old phone, acts as CCTV server
- **Mirror Host** - User's main phone for remote monitoring

## Features

1. **Camera Surveillance** - Front/back camera streaming with screen-off recording
2. **Audio Monitoring** - Live microphone access with recording
3. **GPS Tracking** - Real-time location tracking with geofencing
4. **Screen Mirroring** - View target device screen live (useful for theft recovery)
5. **Remote Gallery** - Browse and download photos/videos from target device

## Tech Stack

- **Languages**: Rust (NDK) + Kotlin
- **Video**: H.264 encoding via MediaCodec
- **Audio**: Opus codec
- **Networking**: WebRTC P2P with STUN/TURN fallback
- **Encryption**: AES-256-GCM with X25519 key exchange

## Project Structure

```
mirror/
├── mirror-target/     # Target app (CCTV server)
├── mirror-host/       # Host app (remote client)
├── mirror-core/       # Shared Rust library
├── mirror-signaling/  # Optional signaling server
└── docs/              # Documentation
```

## Quick Start

### Prerequisites

- Android Studio Arctic Fox or later
- Android NDK r25c or later
- Rust toolchain with Android targets

### Build

```bash
# Build Rust core
cd mirror-core
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../mirror-target/app/src/main/jniLibs build

# Build Android apps
cd mirror-target
./gradlew assembleDebug

cd mirror-host
./gradlew assembleDebug
```

## License

MIT License - See LICENSE file for details
