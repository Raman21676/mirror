# Mirror App - Project TODO & Implementation Plan

## Project Overview

**Mirror** is a surveillance Android app that transforms old phones into CCTV/GPS trackers. It consists of two apps:
- **Mirror Target**: Runs on the old phone (camera/mic/GPS server)
- **Mirror Host**: User's main phone (remote monitoring client)

**Tech Stack**: Rust (NDK) + Kotlin/Java hybrid
- Rust handles: H.264 encoding, Opus audio, encryption, stream multiplexing
- Kotlin handles: Android UI, permissions, foreground services, lifecycle

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           MIRROR APP ARCHITECTURE                           │
├───────────────────────────────┬─────────────────────────────────────────────┤
│    TARGET DEVICE (Old Phone)  │      HOST DEVICE (Main Phone)               │
│         Acts as CCTV Server   │         Remote Monitoring View              │
├───────────────────────────────┼─────────────────────────────────────────────┤
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │ Camera2 API             │  │  │ Live Camera View                    │    │
│  │ Front/back, screen-off  │  │  │ ExoPlayer/WebRTC renderer           │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │ AudioRecord/MediaRec    │  │  │ Audio Monitor                       │    │
│  │ Continuous mic, Opus    │  │  │ Live listen + record toggle         │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │ Fused Location Provider │  │  │ Screen Mirror Viewer                │    │
│  │ Battery-efficient GPS   │  │  │ Record stolen device screen         │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │ MediaProjection API     │  │  │ GPS Map Tracker                     │    │
│  │ Live screen capture     │  │  │ Google Maps Lite + geofencing       │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │ Scoped Storage Query    │  │  │ Gallery Browser                     │    │
│  │ Photos & videos access  │  │  │ Remote photo/video access           │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │ Foreground Service      │  │  │ Kotlin/Java UI Layer                │    │
│  │ + Partial Wake Lock     │  │  │                                     │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
├───────────────────────────────┼─────────────────────────────────────────────┤
│  ┌─────────────────────────┐  │  ┌─────────────────────────────────────┐    │
│  │    Rust NDK Core        │  │  │    Rust NDK Core (Host)             │    │
│  │  ├─ H.264 encoding      │  │  │  ├─ Stream decode                   │    │
│  │  ├─ Opus audio          │  │  │  ├─ Decryption                      │    │
│  │  ├─ E2E encryption      │  │  │  ├─ Recording merge (video+audio)   │    │
│  │  └─ Stream mux          │  │  │                                     │    │
│  └─────────────────────────┘  │  └─────────────────────────────────────┘    │
└───────────────────────────────┴─────────────────────────────────────────────┘
                              │
                              ▼
        ┌─────────────────────────────────────────┐
        │      ENCRYPTED TRANSPORT LAYER          │
        │  ├─ WebRTC P2P (preferred)              │
        │  ├─ STUN/TURN relay fallback            │
        │  └─ QR/PIN pairing + auth token         │
        └─────────────────────────────────────────┘
```

---

## PHASE 1: Research & Environment Setup
**Duration**: 1-2 weeks  
**Goal**: Validate core technical assumptions and establish build pipeline

### Phase 1.1: Android Development Environment
- [ ] Install Android Studio Arctic Fox or later
- [ ] Set up Android SDK (API 26-34)
- [ ] Configure Android NDK (r25c or later)
- [ ] Create Android Virtual Devices for testing (API 28, 33)
- [ ] Enable USB debugging on physical test devices
- [ ] **Deliverable**: Hello World Android app running on both devices

### Phase 1.2: Rust Android Toolchain Setup
- [ ] Install Rust via rustup: `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`
- [ ] Install cargo-ndk: `cargo install cargo-ndk`
- [ ] Add Android targets:
  - [ ] `rustup target add aarch64-linux-android`
  - [ ] `rustup target add armv7-linux-androideabi`
  - [ ] `rustup target add x86_64-linux-android`
- [ ] Configure ANDROID_NDK_HOME environment variable
- [ ] **Deliverable**: Rust "Hello from Rust" JNI call working in Android

### Phase 1.3: Critical Android API Research
- [ ] **Camera2 API Research**:
  - [ ] Study CameraDevice with offscreen SurfaceTexture (no preview needed)
  - [ ] Test screen-off camera capture with `PARTIAL_WAKE_LOCK`
  - [ ] Document `foregroundServiceType="camera"` requirements (Android 10+)
  - [ ] **Risk Mitigation**: Confirm screen-off recording works on Android 10-15
  
- [ ] **MediaProjection API Research**:
  - [ ] Study system dialog requirement (cannot be bypassed)
  - [ ] Test screen mirroring capture with MediaProjection
  - [ ] Document user consent flow requirements
  
- [ ] **Foreground Service Research**:
  - [ ] Study Android 11-15 foreground service type declarations
  - [ ] Test service survival through battery optimization
  - [ ] Research OEM-specific battery killer behaviors (Xiaomi, Huawei, OnePlus, Samsung)
  - [ ] **Deliverable**: Research document with findings and workarounds

### Phase 1.4: Networking & WebRTC Research
- [ ] Study WebRTC P2P connection establishment
- [ ] Research STUN/TURN server requirements for NAT traversal
- [ ] Design QR code pairing protocol (encode ICE candidates, auth tokens)
- [ ] **Deliverable**: Network protocol design document

### Phase 1.5: Project Structure Initialization
- [ ] Create monorepo structure:
  ```
  mirror/
  ├── mirror-target/          # Target app (old phone)
  ├── mirror-host/            # Host app (user phone)
  ├── mirror-core/            # Shared Rust library
  ├── mirror-signaling/       # Optional: signaling server
  └── docs/                   # Documentation
  ```
- [ ] Initialize Git repository
- [ ] Create .gitignore for Android/Rust projects
- [ ] **Deliverable**: Repository structure ready for development

---

## PHASE 2: Mirror Target App - Core Services
**Duration**: 3-4 weeks  
**Goal**: Build the Target app that runs on old phones as CCTV server

### Phase 2.1: Android Project Foundation
- [ ] Create Android project with package `com.mirror.target`
- [ ] Configure minSdk=26, targetSdk=34
- [ ] Set up CMake/NDK integration for Rust
- [ ] Create basic UI: Setup screen, Status screen
- [ ] **Deliverable**: Basic app launching with Rust integration

### Phase 2.2: Foreground Service Architecture
- [ ] Create `MirrorTargetService` extending `Service`
- [ ] Declare service in AndroidManifest.xml:
  ```xml
  <service
      android:name=".service.MirrorTargetService"
      android:foregroundServiceType="camera|microphone|location"
      android:exported="false" />
  ```
- [ ] Implement service lifecycle: onCreate, onStartCommand, onBind
- [ ] Create persistent notification with controls (stop, status)
- [ ] Implement `PARTIAL_WAKE_LOCK` for CPU keep-alive
- [ ] **Deliverable**: Service runs persistently with notification

### Phase 2.3: Permission Management System
- [ ] Implement runtime permission requests:
  - [ ] `CAMERA`
  - [ ] `RECORD_AUDIO`
  - [ ] `ACCESS_FINE_LOCATION`
  - [ ] `ACCESS_COARSE_LOCATION`
  - [ ] `FOREGROUND_SERVICE`
  - [ ] `FOREGROUND_SERVICE_CAMERA`
  - [ ] `FOREGROUND_SERVICE_MICROPHONE`
  - [ ] `FOREGROUND_SERVICE_LOCATION`
- [ ] Create permission onboarding flow
- [ ] Handle "Don't ask again" scenarios with settings redirect
- [ ] **Deliverable**: All permissions granted and validated

### Phase 2.4: Camera2 Implementation
- [ ] Create `CameraCaptureManager` class
- [ ] Implement camera selection (front/back toggle)
- [ ] Set up `CameraDevice` and `CameraCaptureSession`
- [ ] Configure offscreen `SurfaceTexture` (no preview surface)
- [ ] Implement ImageReader for frame capture
- [ ] Add camera state machine (IDLE, OPENING, ACTIVE, ERROR)
- [ ] Handle camera disconnections and reconnection
- [ ] **Deliverable**: Camera captures frames with screen off

### Phase 2.5: Audio Capture Implementation
- [ ] Create `AudioCaptureManager` class
- [ ] Set up `AudioRecord` with:
  - Sample rate: 48000 Hz
  - Channel: MONO
  - Format: PCM_16BIT
  - Buffer size: calculate via `getMinBufferSize`
- [ ] Implement audio recording loop in background thread
- [ ] Add audio level monitoring for VU meter
- [ ] **Deliverable**: Continuous audio capture working

### Phase 2.6: GPS Location Tracking
- [ ] Create `LocationTracker` class
- [ ] Integrate Google Play Services Location
- [ ] Configure Fused Location Provider:
  - [ ] Fast updates when moving (5-second interval)
  - [ ] Slow updates when stationary (60-second interval)
  - [ ] Balanced power priority
- [ ] Implement location caching for offline scenarios
- [ ] Add geofencing capability (enter/exit zone alerts)
- [ ] **Deliverable**: Battery-efficient location updates working

### Phase 2.7: Screen Mirroring (MediaProjection)
- [ ] Create `ScreenCaptureManager` class
- [ ] Implement MediaProjection permission request flow
- [ ] Set up VirtualDisplay for screen capture
- [ ] Handle screen rotation events
- [ ] **Note**: System dialog approval required - cannot be hidden
- [ ] **Deliverable**: Screen capture working with user consent

### Phase 2.8: Gallery Access (Scoped Storage)
- [ ] Create `GalleryManager` class
- [ ] Query MediaStore for images and videos
- [ ] Implement pagination for large galleries
- [ ] Handle storage permissions for Android 10+ (scoped storage)
- [ ] Create thumbnail generation
- [ ] **Deliverable**: Gallery listing and thumbnail generation

---

## PHASE 3: Mirror Core - Rust Native Library
**Duration**: 3-4 weeks (parallel with Phase 2.5+)  
**Goal**: Build high-performance Rust core for encoding and encryption

### Phase 3.1: Rust Project Setup
- [ ] Create `mirror-core` crate structure:
  ```
  mirror-core/
  ├── Cargo.toml
  ├── src/
  │   ├── lib.rs          # Library entry point
  │   ├── video/          # Video encoding modules
  │   ├── audio/          # Audio encoding modules
  │   ├── crypto/         # Encryption modules
  │   ├── network/        # Networking modules
  │   └── jni/            # JNI bridge modules
  └── build.rs
  ```
- [ ] Configure Cargo for Android cross-compilation
- [ ] Set up jni crate for JNI bindings
- [ ] **Deliverable**: Rust library compiles for all Android targets

### Phase 3.2: Video Encoding (H.264)
- [ ] Research and integrate video encoding library:
  - Option A: `openh264` crate (Cisco's openh264)
  - Option B: Bind to Android's MediaCodec via JNI
  - Option C: Use `x264` encoder
- [ ] Implement `VideoEncoder` struct:
  ```rust
  pub struct VideoEncoder {
      width: u32,
      height: u32,
      fps: u32,
      bitrate: u32,
      encoder: EncoderInstance,
  }
  ```
- [ ] Implement encode_frame(input: &[u8]) -> Vec<u8>
- [ ] Support configuration: resolution, bitrate, frame rate
- [ ] **Deliverable**: Raw camera frames → H.264 NAL units

### Phase 3.3: Audio Encoding (Opus)
- [ ] Integrate `opus` crate or bind to libopus
- [ ] Implement `AudioEncoder` struct:
  ```rust
  pub struct AudioEncoder {
      sample_rate: u32,
      channels: u16,
      bitrate: u32,
  }
  ```
- [ ] Implement encode_packet(input: &[i16]) -> Vec<u8>
- [ ] Configure for VOIP quality (24kbps, 20ms frames)
- [ ] **Deliverable**: Raw PCM → Opus packets

### Phase 3.4: End-to-End Encryption
- [ ] Integrate `ring` or `rustls` for cryptography
- [ ] Implement key exchange protocol:
  - [ ] X25519 for ECDH key exchange
  - [ ] AES-256-GCM for data encryption
  - [ ] HKDF for key derivation
- [ ] Create `CryptoSession` struct managing encryption state
- [ ] Implement encrypt_packet / decrypt_packet
- [ ] **Security Review**: Ensure proper nonce handling, key rotation
- [ ] **Deliverable**: Encrypted packet transmission

### Phase 3.5: Stream Multiplexing
- [ ] Design packet format:
  ```
  [1 byte: stream_type][4 bytes: timestamp][2 bytes: payload_len][payload][16 bytes: auth_tag]
  Stream types: VIDEO=0x01, AUDIO=0x02, LOCATION=0x03, SCREEN=0x04, CONTROL=0xFF
  ```
- [ ] Implement `StreamMux` for combining multiple streams
- [ ] Implement `StreamDemux` for separating streams
- [ ] Handle packet fragmentation for large video frames
- [ ] **Deliverable**: Multiple streams multiplexed into single channel

### Phase 3.6: JNI Bridge Layer
- [ ] Create JNI exports for each Rust module:
  ```rust
  #[no_mangle]
  pub extern "C" fn Java_com_mirror_core_RustBridge_initEncoder(...) -> jlong
  
  #[no_mangle]
  pub extern "C" fn Java_com_mirror_core_RustBridge_encodeFrame(env: ..., obj: ..., handle: jlong, data: jbyteArray) -> jbyteArray
  ```
- [ ] Implement memory-safe JNI calls with proper exception handling
- [ ] Create Java `RustBridge` class as facade
- [ ] **Deliverable**: Kotlin can call all Rust functions

---

## PHASE 4: Mirror Host App - Remote Client
**Duration**: 3-4 weeks  
**Goal**: Build the Host app for user to monitor Target devices

### Phase 4.1: Android Project Foundation
- [ ] Create Android project with package `com.mirror.host`
- [ ] Configure minSdk=26, targetSdk=34
- [ ] Set up Material Design 3 UI components
- [ ] Create bottom navigation: Live, Audio, Map, Gallery, Settings
- [ ] **Deliverable**: Basic UI structure with navigation

### Phase 4.2: Device Pairing System
- [ ] Create pairing flow:
  - [ ] Host displays QR code (contains connection info + auth token)
  - [ ] Target scans QR code
  - [ ] Alternative: PIN-based pairing for devices without camera
- [ ] Implement secure token generation (cryptographically random)
- [ ] Store paired devices in encrypted SharedPreferences
- [ ] Create device management UI (add, remove, rename devices)
- [ ] **Deliverable**: Two devices can pair securely

### Phase 4.3: WebRTC Connection Management
- [ ] Integrate Google WebRTC library (`org.webrtc:google-webrtc`)
- [ ] Implement `WebRtcConnectionManager`:
  - [ ] Create PeerConnectionFactory
  - [ ] Handle ICE candidate gathering
  - [ ] Implement SDP offer/answer exchange
  - [ ] Set up DataChannel for control messages
- [ ] Implement STUN/TURN server configuration
- [ ] Add connection state machine (NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED)
- [ ] **Deliverable**: P2P connection between Host and Target

### Phase 4.4: Live Camera View
- [ ] Create `LiveCameraActivity` with fullscreen video
- [ ] Integrate ExoPlayer or SurfaceView for video rendering
- [ ] Implement H.264 decoding (via MediaCodec or WebRTC)
- [ ] Add overlay controls: record toggle, snapshot, camera switch
- [ ] Implement pinch-to-zoom for digital zoom
- [ ] **Deliverable**: Live video stream displayed in real-time

### Phase 4.5: Audio Monitor
- [ ] Create `AudioMonitorActivity` with VU meter visualization
- [ ] Implement Opus audio decoding
- [ ] Add audio playback via AudioTrack
- [ ] Implement recording toggle (save to local file)
- [ ] Add push-to-talk feature (send audio back to Target)
- [ ] **Deliverable**: Live audio streaming with recording

### Phase 4.6: GPS Map Tracker
- [ ] Integrate Google Maps SDK
- [ ] Create `MapTrackerActivity` with real-time location display
- [ ] Implement location marker updates
- [ ] Add location history trail (polyline)
- [ ] Implement geofencing alerts:
  - [ ] Circle geofence drawing
  - [ ] Enter/exit notifications
  - [ ] SMS fallback for low connectivity
- [ ] **Deliverable**: Real-time location tracking on map

### Phase 4.7: Screen Mirror Viewer
- [ ] Create `ScreenMirrorActivity`
- [ ] Implement video stream rendering for screen capture
- [ ] Add recording toggle for evidence capture
- [ ] Handle different screen orientations
- [ ] **Deliverable**: Live screen mirroring with recording

### Phase 4.8: Gallery Browser
- [ ] Create `GalleryBrowserActivity` with RecyclerView grid
- [ ] Implement thumbnail caching (Glide or Coil)
- [ ] Add download functionality for remote files
- [ ] Implement video playback with ExoPlayer
- [ ] Add delete remote file functionality
- [ ] **Deliverable**: Remote gallery browsing and download

### Phase 4.9: Recording & Storage
- [ ] Create `RecordingManager` for local recordings
- [ ] Implement MP4 muxing (video + audio) using MediaMuxer
- [ ] Add recording scheduler (start/stop at times)
- [ ] Create recordings browser UI
- [ ] Implement cloud backup option (optional)
- [ ] **Deliverable**: Recordings saved and playable

---

## PHASE 5: Advanced Features & Optimization
**Duration**: 2-3 weeks  
**Goal**: Add intelligence, reduce battery usage, improve performance

### Phase 5.1: Motion Detection
- [ ] Research motion detection algorithms:
  - Option A: Frame differencing (simple, low CPU)
  - Option B: Background subtraction
  - Option C: ML-based detection (TensorFlow Lite)
- [ ] Implement motion detection in Rust or via OpenCV
- [ ] Add adaptive FPS: full fps when motion detected, 5fps when idle
- [ ] Send motion alerts to Host
- [ ] **Deliverable**: Motion-aware streaming, battery savings

### Phase 5.2: Battery Optimization
- [ ] Implement adaptive quality based on battery level:
  - [ ] Above 50%: Full quality
  - [ ] 20-50%: Reduced FPS, lower bitrate
  - [ ] Below 20%: Essential features only (GPS, low-res camera)
- [ ] Optimize wake locks (use `Doze` aware alarms)
- [ ] Implement efficient buffer pools to reduce GC pressure
- [ ] **Target**: Under 10% battery drain per hour on mid-range device
- [ ] **Deliverable**: Battery usage within target

### Phase 5.3: Network Resilience
- [ ] Implement connection retry with exponential backoff
- [ ] Add offline mode on Target (cache data when disconnected)
- [ ] Implement adaptive bitrate based on network quality
- [ ] Add SMS fallback for critical alerts (geofence breach)
- [ ] **Deliverable**: Robust connection handling

### Phase 5.4: Security Hardening
- [ ] Implement certificate pinning for signaling server
- [ ] Add tamper detection (root detection, debug detection)
- [ ] Implement secure logging (no sensitive data in logs)
- [ ] Add session timeout and re-authentication
- [ ] **Security Audit**: Review all encryption and authentication
- [ ] **Deliverable**: Security hardened, penetration tested

### Phase 5.5: OEM Compatibility
- [ ] Research and implement OEM-specific battery whitelisting:
  - [ ] Xiaomi: Auto-start permission, battery saver ignore
  - [ ] Huawei: Protected apps, battery optimization whitelist
  - [ ] OnePlus: Battery optimization, app locker
  - [ ] Samsung: Put unused apps to sleep exclusion
- [ ] Create onboarding wizard guiding users through settings
- [ ] **Deliverable**: Works reliably across major OEMs

---

## PHASE 6: Testing & Quality Assurance
**Duration**: 2 weeks  
**Goal**: Ensure stability, performance, and compatibility

### Phase 6.1: Unit Testing
- [ ] Rust unit tests for core library functions
- [ ] Kotlin unit tests for Android business logic
- [ ] Mock testing for Android components
- [ ] **Deliverable**: >70% code coverage

### Phase 6.2: Integration Testing
- [ ] Test Target-Host connection scenarios
- [ ] Test reconnection after network loss
- [ ] Test permissions denied scenarios
- [ ] Test service lifecycle (app killed, rebooted)
- [ ] **Deliverable**: Integration test suite passing

### Phase 6.3: Device Compatibility Testing
- [ ] Test on Android 8.0 (API 26) - minimum supported
- [ ] Test on Android 10 (API 29) - scoped storage changes
- [ ] Test on Android 12 (API 31) - notification changes
- [ ] Test on Android 14 (API 34) - latest stable
- [ ] Test on various screen sizes (phone, tablet)
- [ ] Test on low-RAM devices (1-2GB)
- [ ] **Deliverable**: Compatibility matrix document

### Phase 6.4: Performance Testing
- [ ] Measure battery drain (Target app)
- [ ] Measure latency (video, audio, location)
- [ ] Measure memory usage
- [ ] Stress test: 24-hour continuous streaming
- [ ] **Deliverable**: Performance benchmark report

### Phase 6.5: Security Testing
- [ ] Run static analysis (MobSF, OWASP MASVS)
- [ ] Test encryption implementation
- [ ] Verify no hardcoded secrets
- [ ] Check for common Android vulnerabilities
- [ ] **Deliverable**: Security assessment report

---

## PHASE 7: Documentation & Deployment
**Duration**: 1-2 weeks  
**Goal**: Prepare for distribution

### Phase 7.1: User Documentation
- [ ] Create user guide with screenshots
- [ ] Write setup tutorial (Target app installation)
- [ ] Document OEM-specific battery settings
- [ ] Create FAQ document
- [ ] **Deliverable**: Complete user documentation

### Phase 7.2: Developer Documentation
- [ ] Document architecture decisions
- [ ] Write API documentation (auto-generated from code)
- [ ] Create contribution guidelines
- [ ] Document build process
- [ ] **Deliverable**: Developer documentation

### Phase 7.3: Build & Release
- [ ] Set up CI/CD pipeline (GitHub Actions)
- [ ] Create release signing configuration
- [ ] Build release APKs (Target and Host)
- [ ] Optimize APK size (ProGuard/R8)
- [ ] **Deliverable**: Signed release APKs

### Phase 7.4: Distribution
- [ ] **F-Droid** (recommended first):
  - [ ] Create F-Droid metadata
  - [ ] Submit to F-Droid repository
  - [ ] Comply with F-Droid inclusion policy
- [ ] **GitHub Releases**:
  - [ ] Create release notes
  - [ ] Attach APKs to release
  - [ ] Provide checksums
- [ ] **Play Store** (if desired):
  - [ ] Review Play Store policies on surveillance apps
  - [ ] Implement prominent disclosure flows
  - [ ] Position as "home security / anti-theft" tool
  - [ ] Submit for review
- [ ] **Deliverable**: App available for download

---

## Appendix A: File Structure

```
mirror/
├── AGENTS.md                          # Project context for AI agents
├── PROJECT_TODO.md                    # This file
├── README.md                          # Project overview
├── LICENSE                            # Open source license
│
├── mirror-target/                     # Target App (CCTV Server)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── AndroidManifest.xml
│   │   │   │   ├── java/com/mirror/target/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── MirrorTargetService.kt
│   │   │   │   │   │   └── ServiceRestarter.kt
│   │   │   │   │   ├── camera/
│   │   │   │   │   │   └── CameraCaptureManager.kt
│   │   │   │   │   ├── audio/
│   │   │   │   │   │   └── AudioCaptureManager.kt
│   │   │   │   │   ├── location/
│   │   │   │   │   │   └── LocationTracker.kt
│   │   │   │   │   ├── screen/
│   │   │   │   │   │   └── ScreenCaptureManager.kt
│   │   │   │   │   ├── gallery/
│   │   │   │   │   │   └── GalleryManager.kt
│   │   │   │   │   ├── pairing/
│   │   │   │   │   │   └── PairingManager.kt
│   │   │   │   │   └── core/
│   │   │   │   │       └── RustBridge.kt
│   │   │   │   └── res/
│   │   └── build.gradle.kts
│   └── build.gradle.kts
│
├── mirror-host/                       # Host App (Remote Client)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── AndroidManifest.xml
│   │   │   │   ├── java/com/mirror/host/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── live/
│   │   │   │   │   │   ├── LiveCameraActivity.kt
│   │   │   │   │   │   └── LiveCameraViewModel.kt
│   │   │   │   │   ├── audio/
│   │   │   │   │   │   ├── AudioMonitorActivity.kt
│   │   │   │   │   │   └── AudioVisualizerView.kt
│   │   │   │   │   ├── map/
│   │   │   │   │   │   ├── MapTrackerActivity.kt
│   │   │   │   │   │   └── GeofenceManager.kt
│   │   │   │   │   ├── screen/
│   │   │   │   │   │   └── ScreenMirrorActivity.kt
│   │   │   │   │   ├── gallery/
│   │   │   │   │   │   ├── GalleryBrowserActivity.kt
│   │   │   │   │   │   └── GalleryAdapter.kt
│   │   │   │   │   ├── pairing/
│   │   │   │   │   │   ├── DevicePairingActivity.kt
│   │   │   │   │   │   └── QrCodeGenerator.kt
│   │   │   │   │   ├── webrtc/
│   │   │   │   │   │   └── WebRtcConnectionManager.kt
│   │   │   │   │   ├── recording/
│   │   │   │   │   │   └── RecordingManager.kt
│   │   │   │   │   └── core/
│   │   │   │   │       └── RustBridge.kt
│   │   │   │   └── res/
│   │   └── build.gradle.kts
│   └── build.gradle.kts
│
├── mirror-core/                       # Shared Rust Library
│   ├── Cargo.toml
│   ├── build.rs
│   └── src/
│       ├── lib.rs
│       ├── video/
│       │   ├── mod.rs
│       │   ├── encoder.rs
│       │   └── decoder.rs
│       ├── audio/
│       │   ├── mod.rs
│       │   ├── opus_encoder.rs
│       │   └── opus_decoder.rs
│       ├── crypto/
│       │   ├── mod.rs
│       │   ├── session.rs
│       │   └── packet.rs
│       ├── network/
│       │   ├── mod.rs
│       │   ├── mux.rs
│       │   ├── demux.rs
│       │   └── webrtc_bridge.rs
│       └── jni/
│           ├── mod.rs
│           ├── bridge.rs
│           └── utils.rs
│
└── mirror-signaling/                  # Optional: Signaling Server
    ├── Cargo.toml
    └── src/
        └── main.rs
```

---

## Appendix B: Technology Stack Summary

| Component | Technology | Rationale |
|-----------|------------|-----------|
| **Language - Core** | Rust + NDK | Memory safety, performance, small binary size |
| **Language - UI** | Kotlin | Native Android APIs, modern language features |
| **Build System** | Gradle (Android), Cargo (Rust) | Standard tooling |
| **Camera** | Camera2 API | Full manual control, screen-off capture |
| **Audio** | AudioRecord + Opus | Low latency, efficient compression |
| **Video Codec** | H.264 (MediaCodec/openh264) | Hardware acceleration, wide support |
| **Audio Codec** | Opus | Best quality/bitrate ratio |
| **Location** | Fused Location Provider | Battery-efficient GPS |
| **Screen Capture** | MediaProjection API | System-level screen access |
| **Networking** | WebRTC P2P | NAT traversal, low latency |
| **Encryption** | AES-256-GCM + X25519 | Industry-standard E2E encryption |
| **Signaling** | QR/PIN + WebSocket/Firebase | Simple, reliable pairing |
| **Video Rendering** | ExoPlayer/SurfaceView | Hardware-accelerated playback |
| **Maps** | Google Maps SDK | Feature-rich, well-documented |

---

## Appendix C: Android Permissions Required

### Target App Permissions
```xml
<!-- Core functionality -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<!-- Screen capture -->
<uses-permission android:name="android.permission.PROJECT_MEDIA" />

<!-- Storage -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

<!-- Power management -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Host App Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" /> <!-- For push-to-talk -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /> <!-- For saving recordings -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- For map display -->
<uses-permission android:name="android.permission.CAMERA" /> <!-- For QR scanning -->
```

---

## Appendix D: Risk Assessment & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Screen-off camera blocked by OEM | High | Critical | Test early (Phase 1), document workarounds |
| Battery killers terminate service | High | High | Implement OEM-specific whitelisting guides |
| NAT traversal fails | Medium | High | Always have TURN relay fallback |
| Play Store rejection | Medium | Medium | Start with F-Droid, position as security tool |
| Performance on old devices | Medium | Medium | Adaptive quality, hardware codec preference |
| Security vulnerabilities | Low | Critical | Regular audits, use established crypto libs |
| Background location restrictions | Medium | High | Use foreground service type, clear user consent |

---

## Progress Tracking

| Phase | Status | Started | Completed |
|-------|--------|---------|-----------|
| Phase 1: Research & Environment | 🟢 Completed | 2026-04-03 | 2026-04-04 |
| Phase 2: Target App Core | 🟡 In Progress | 2026-04-04 | - |
| Phase 3: Rust Core Library | 🟡 In Progress | 2026-04-04 | - |
| Phase 4: Host App | 🔴 Not Started | - | - |
| Phase 5: Advanced Features | ⏸️ Skipped for now | - | - |
| Phase 6: Testing | 🔴 Not Started | - | - |
| Phase 7: Deployment | 🔴 Not Started | - | - |

**Legend**: 🔴 Not Started | 🟡 In Progress | 🟢 Completed | ⏸️ Blocked

---

*Last Updated: 2026-04-04*  
*Next Review: Task 3 - TCP client in Host app*
