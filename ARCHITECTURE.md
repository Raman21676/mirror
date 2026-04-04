# Mirror App - Architecture Documentation

> **Version**: 0.1.0  
> **Last Updated**: 2026-04-03  
> **Status**: Draft

---

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Component Architecture](#component-architecture)
4. [Data Flow](#data-flow)
5. [Security Architecture](#security-architecture)
6. [Technology Stack](#technology-stack)
7. [Deployment Architecture](#deployment-architecture)
8. [Appendices](#appendices)

---

## Overview

### Purpose

Mirror is an Android surveillance system that transforms old smartphones into CCTV cameras, GPS trackers, and remote monitoring devices. It enables users to:

- Monitor their property using an old phone as a security camera
- Track vehicle locations in real-time
- Recover stolen devices via screen mirroring and location tracking
- Access device gallery remotely

### Key Design Principles

| Principle | Description |
|-----------|-------------|
| **Battery Efficiency** | Target app must use <10% battery per hour |
| **Privacy First** | End-to-end encryption for all data streams |
| **Reliability** | Works across different Android OEMs and versions |
| **Low Latency** | Real-time streaming with minimal delay |
| **Offline Capability** | Graceful degradation when network is unavailable |

---

## System Architecture

### High-Level Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MIRROR SYSTEM                                  │
├────────────────────────────────┬────────────────────────────────────────────┤
│      TARGET DEVICE             │          HOST DEVICE                       │
│    (Old Phone / Server)        │      (User's Main Phone)                   │
├────────────────────────────────┼────────────────────────────────────────────┤
│                                │                                            │
│  ┌────────────────────────┐   │   ┌──────────────────────────────────┐     │
│  │   Android Framework    │   │   │     Android Framework            │     │
│  │  ├─ Camera2 API        │   │   │    ├─ ExoPlayer                 │     │
│  │  ├─ AudioRecord        │   │   │    ├─ Google Maps SDK           │     │
│  │  ├─ Fused Location     │   │   │    ├─ MediaProjection           │     │
│  │  ├─ MediaProjection    │   │   │    └─ MediaStore                │     │
│  │  └─ MediaStore         │   │   │                                  │     │
│  └────────────────────────┘   │   └──────────────────────────────────┘     │
│           │                    │                    │                       │
│  ┌────────▼────────────────┐   │   ┌───────────────▼──────────────────┐     │
│  │     Kotlin Layer        │   │   │        Kotlin Layer              │     │
│  │  ├─ Foreground Service  │   │   │    ├─ UI Activities             │     │
│  │  ├─ Capture Managers    │   │   │    ├─ WebRTC Manager            │     │
│  │  └─ JNI Bridge          │   │   │    ├─ Recording Manager         │     │
│  └─────────────────────────┘   │   │    └─ JNI Bridge                │     │
│           │                    │   └──────────────────────────────────┘     │
│  ┌────────▼────────────────┐   │                    │                       │
│  │      Rust NDK Core      │◄──┼──────────────────►│    Rust NDK Core       │
│  │  ├─ Video Encoder       │   │    Encrypted      │    ├─ Video Decoder    │
│  │  ├─ Audio Encoder       │   │      P2P          │    ├─ Audio Decoder    │
│  │  ├─ Stream Mux          │   │    Connection     │    ├─ Stream Demux     │
│  │  └─ Encryption          │   │                   │    └─ Decryption       │
│  └─────────────────────────┘   │                   └────────────────────────┘
│                                │                                            │
└────────────────────────────────┴────────────────────────────────────────────┘
                              │
                              │  Signaling (WebSocket/Firebase)
                              │  - Connection setup
                              │  - ICE candidate exchange
                              ▼
                    ┌─────────────────────┐
                    │   Signaling Server  │
                    │   (Optional/SaaS)   │
                    └─────────────────────┘
```

### Two-App Model

| Aspect | Mirror Target | Mirror Host |
|--------|--------------|-------------|
| **Role** | Data Producer (Server) | Data Consumer (Client) |
| **Primary Function** | Capture & Stream | Monitor & Record |
| **User Interaction** | Minimal (setup only) | Continuous |
| **Resource Usage** | High (CPU, Camera, Battery) | Medium (Display, Network) |
| **Network Role** | Waits for connection | Initiates connection |

---

## Component Architecture

### 1. Mirror Target Components

```
mirror-target/
├── service/
│   └── MirrorTargetService          # Foreground service coordinator
├── camera/
│   └── CameraCaptureManager         # Camera2 API wrapper
├── audio/
│   └── AudioCaptureManager          # AudioRecord wrapper
├── location/
│   └── LocationTracker              # FusedLocationProvider wrapper
├── screen/
│   └── ScreenCaptureManager         # MediaProjection wrapper
├── gallery/
│   └── GalleryManager               # MediaStore query handler
├── pairing/
│   └── PairingManager               # QR/PIN authentication
└── core/
    └── RustBridge                   # JNI interface to Rust
```

#### 1.1 MirrorTargetService

```kotlin
class MirrorTargetService : Service() {
    // Lifecycle
    onCreate() -> Initialize managers
    onStartCommand() -> Start capture loops
    onBind() -> Not used (started service)
    onDestroy() -> Cleanup
    
    // Components
    - WakeLock (PARTIAL_WAKE_LOCK)
    - Notification (persistent)
    - CameraCaptureManager
    - AudioCaptureManager
    - LocationTracker
}
```

**Key Design Decisions:**
- Uses `START_STICKY` for auto-restart after kill
- Declares `foregroundServiceType="camera|microphone|location"`
- Acquires partial wake lock to prevent CPU sleep

#### 1.2 CameraCaptureManager

```kotlin
class CameraCaptureManager {
    // State Machine
    IDLE -> OPENING -> ACTIVE -> [ERROR]
    
    // Key Implementation
    - Uses CameraDevice.TEMPLATE_RECORD for efficiency
    - No SurfaceTexture (no preview) for screen-off operation
    - ImageReader for frame capture
    - Background HandlerThread for callbacks
}
```

**Critical Constraints:**
- Screen-off capture requires foreground service
- Some OEMs aggressively kill camera processes
- Must handle camera disconnections gracefully

#### 1.3 AudioCaptureManager

```kotlin
class AudioCaptureManager {
    // Configuration
    - Source: MediaRecorder.AudioSource.VOICE_RECOGNITION
    - Sample Rate: 48000 Hz
    - Format: PCM_16BIT
    - Channels: MONO
    
    // Processing
    - Continuous read loop in Coroutine
    - Buffer size: 2x minimum for safety
}
```

#### 1.4 LocationTracker

```kotlin
class LocationTracker {
    // Strategy
    - Priority: PRIORITY_BALANCED_POWER_ACCURACY
    - Adaptive interval based on movement
    - Fast updates (5s) when moving
    - Slow updates (60s) when stationary
}
```

### 2. Mirror Host Components

```
mirror-host/
├── live/
│   └── LiveCameraActivity           # Video streaming UI
├── audio/
│   └── AudioMonitorActivity         # Audio streaming UI
├── map/
│   └── MapTrackerActivity           # GPS tracking UI
├── screen/
│   └── ScreenMirrorActivity         # Screen mirroring UI
├── gallery/
│   └── GalleryBrowserActivity       # Remote gallery UI
├── pairing/
│   └── DevicePairingActivity        # Device connection UI
├── webrtc/
│   └── WebRtcConnectionManager      # P2P connection handler
├── recording/
│   └── RecordingManager             # Local recording handler
└── core/
    └── RustBridge                   # JNI interface to Rust
```

#### 2.1 WebRtcConnectionManager

```kotlin
class WebRtcConnectionManager {
    // Components
    - PeerConnectionFactory
    - PeerConnection
    - DataChannel (for control messages)
    
    // Flow
    1. Create offer
    2. Send offer to target (via signaling)
    3. Receive answer
    4. Exchange ICE candidates
    5. Connection established
    
    // Fallback
    - TURN server for symmetric NAT
    - Relay if P2P fails
}
```

### 3. Mirror Core (Rust) Components

```
mirror-core/
├── video/
│   ├── encoder.rs                   # H.264 encoding
│   └── decoder.rs                   # H.264 decoding
├── audio/
│   ├── encoder.rs                   # Opus encoding
│   └── decoder.rs                   # Opus decoding
├── crypto/
│   └── session.rs                   # Encryption session
├── network/
│   ├── mux.rs                       # Stream multiplexing
│   └── demux.rs                     # Stream demultiplexing
└── jni_bridge/
    └── mod.rs                       # JNI exports
```

#### 3.1 Video Pipeline

```rust
// Input: Raw camera frame (NV12/NV21)
RawFrame {
    data: Vec<u8>,
    width: u32,
    height: u32,
    timestamp_ms: u64,
}

// Processing
VideoEncoder::encode(raw_frame) -> EncodedFrame

// Output: H.264 NAL units
EncodedFrame {
    data: Vec<u8>,       // H.264 data
    timestamp_ms: u64,
    is_keyframe: bool,
}
```

#### 3.2 Audio Pipeline

```rust
// Input: Raw PCM samples
RawAudio {
    samples: Vec<i16>,
    timestamp_ms: u64,
}

// Processing
AudioEncoder::encode(raw_audio) -> EncodedAudio

// Output: Opus packets
EncodedAudio {
    data: Vec<u8>,
    timestamp_ms: u64,
}
```

#### 3.3 Encryption Layer

```rust
// Key Exchange: X25519 ECDH
// Encryption: AES-256-GCM
// Authentication: 16-byte GCM tag

struct CryptoSession {
    encryption_key: [u8; 32],
    decryption_key: [u8; 32],
}

impl CryptoSession {
    fn encrypt(&self, plaintext: &[u8]) -> EncryptedPacket
    fn decrypt(&self, packet: &EncryptedPacket) -> Vec<u8>
}
```

#### 3.4 Stream Multiplexing

```
Packet Format:
┌────────┬────────────┬────────┬─────────┬──────────┬─────────────┐
│  Type  │ Timestamp  │ Length │ Payload │ Auth Tag │
│ 1 byte │ 4 bytes    │ 2 bytes│ N bytes │ 16 bytes │
└────────┴────────────┴────────┴─────────┴──────────┴─────────────┘

Stream Types:
- 0x01: Video (H.264)
- 0x02: Audio (Opus)
- 0x03: Location (JSON)
- 0x04: Screen (H.264)
- 0xFF: Control
```

---

## Data Flow

### 1. Video Streaming Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        VIDEO STREAM FLOW                        │
└─────────────────────────────────────────────────────────────────┘

Target Device                                    Host Device
─────────────                                    ───────────
Camera2 API
    │
    ▼
Raw Frame (NV12)
    │
    ▼
Rust VideoEncoder (H.264)
    │
    ▼
StreamMux ───────────────────► Network ─────────► StreamDemux
    │                                               │
    │                                               ▼
    │                                          Rust VideoDecoder
    │                                               │
    │                                               ▼
    │                                          ExoPlayer/SurfaceView
    │                                               │
    │                                               ▼
    │                                          Display
```

### 2. Audio Streaming Flow

```
AudioRecord ──► Rust AudioEncoder (Opus) ──► StreamMux 
                                                  │
                                                  ▼
Network ──► StreamDemux ──► Rust AudioDecoder ──► AudioTrack
                                                        │
                                                        ▼
                                                   Speaker
```

### 3. Location Update Flow

```
Fused Location Provider
    │
    ▼
Location Data (lat, lon, accuracy, timestamp)
    │
    ▼
JSON Serialization
    │
    ▼
StreamMux ──► Network ──► StreamDemux
                              │
                              ▼
                         Google Maps SDK
                              │
                              ▼
                         Map Marker Update
```

---

## Security Architecture

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Unauthorized access | Pairing required (QR/PIN) |
| Man-in-the-middle | E2E encryption (AES-256-GCM) |
| Replay attacks | Unique nonce per packet |
| Data tampering | GCM authentication tag |
| Key compromise | Ephemeral key exchange (X25519) |

### Encryption Protocol

```
Initial Pairing:
1. Host generates keypair (X25519)
2. Public key encoded in QR code
3. Target scans and generates its keypair
4. Both derive shared secret via ECDH
5. Keys derived via HKDF-SHA256

Session Keys:
- Encryption key = HKDF(shared_secret, "encryption")
- Decryption key = HKDF(shared_secret, "decryption")

Per-Packet Encryption:
1. Generate random nonce (12 bytes)
2. Encrypt plaintext with AES-256-GCM
3. Append authentication tag (16 bytes)
4. Send: nonce + ciphertext + tag
```

### Certificate Pinning (Optional)

For signaling server communication:
- Pin server's TLS certificate
- Prevent MITM on signaling channel

---

## Technology Stack

### Languages

| Component | Language | Reason |
|-----------|----------|--------|
| Performance Core | Rust | Memory safety, performance, small binaries |
| Android UI | Kotlin | Native Android API access, modern features |
| JNI Bridge | Rust/C | Interface between Rust and Kotlin |

### Libraries & Frameworks

| Purpose | Library | Version |
|---------|---------|---------|
| Video Encoding | MediaCodec (Android) / openh264 | System / 2.3.1 |
| Audio Encoding | Opus | 1.4 |
| Encryption | ring | 0.17 |
| Networking | WebRTC | 1.0.32006 |
| Maps | Google Maps SDK | 18.2.0 |
| Video Player | ExoPlayer | 1.2.1 |
| QR Codes | ZXing | 4.3.0 |

### Build Tools

| Tool | Purpose |
|------|---------|
| cargo-ndk | Rust cross-compilation for Android |
| Gradle | Android build system |
| CMake | Native code build |
| ProGuard/R8 | Code obfuscation and shrinking |

---

## Deployment Architecture

### Distribution Channels

```
┌─────────────────────────────────────────────────────────┐
│                    DISTRIBUTION                         │
├─────────────────┬─────────────────┬─────────────────────┤
│    F-Droid      │  GitHub Releases │   Play Store        │
│   (Primary)     │   (Secondary)    │   (Optional)        │
├─────────────────┼─────────────────┼─────────────────────┤
│ • Open source   │ • APK downloads │ • Requires policy   │
│ • No cost       │ • Release notes │   compliance        │
│ • Fast updates  │ • Checksums     │ • Potential removal │
│ • FOSS ethos    │ • Source code   │   risk              │
└─────────────────┴─────────────────┴─────────────────────┘
```

### Server Requirements

**Optional Signaling Server:**
- Lightweight WebSocket server
- Handles connection setup only
- No media relay (use TURN if needed)
- Can use Firebase as alternative

**TURN Server (Optional):**
- Required for symmetric NAT traversal
- Can use free services (e.g., Twilio, Xirsys)
- Or self-hosted (coturn)

---

## Appendices

### A. API Levels Support

| API Level | Android Version | Support Status |
|-----------|-----------------|----------------|
| 26 | 8.0 (Oreo) | ✅ Minimum |
| 28 | 9.0 (Pie) | ✅ Supported |
| 29 | 10 (Q) | ✅ Supported |
| 30 | 11 (R) | ✅ Supported |
| 31 | 12 (S) | ✅ Supported |
| 33 | 13 (Tiramisu) | ✅ Supported |
| 34 | 14 (UpsideDownCake) | ✅ Target |

### B. OEM Compatibility

| OEM | Known Issues | Workaround |
|-----|--------------|------------|
| Samsung | Background restrictions | Request battery optimization exemption |
| Xiaomi | Auto-start disabled | Guide user to enable auto-start |
| Huawei | Aggressive doze mode | Add to protected apps |
| OnePlus | App locker | Disable battery optimization |
| Oppo/Realme | Background freeze | Lock app in recents |

### C. Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| Battery drain | <10%/hour | Battery historian |
| Video latency | <500ms | Frame timestamp diff |
| Audio latency | <300ms | Packet timestamp diff |
| Location accuracy | <10m | GPS precision |
| Startup time | <3s | App launch to first frame |

### D. Network Requirements

| Stream Type | Bandwidth (Min) | Bandwidth (Recommended) |
|-------------|-----------------|-------------------------|
| Video (Low) | 256 kbps | 512 kbps |
| Video (Med) | 512 kbps | 1 Mbps |
| Video (High) | 1 Mbps | 2 Mbps |
| Audio | 24 kbps | 64 kbps |
| Location | 1 kbps | 1 kbps |
| Screen Mirror | 1 Mbps | 4 Mbps |

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 0.1.0 | 2026-04-03 | Initial architecture document |
