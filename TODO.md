# Mirror App - TODO List

> **Project**: Mirror Surveillance App  
> **Last Updated**: 2026-04-04  
> **Status**: 🟡 Phase 2/3 - In Progress

---

## Legend

| Symbol | Meaning |
|--------|---------|
| 🔴 | Not Started |
| 🟡 | In Progress |
| 🟢 | Completed |
| ⏸️ | Blocked |
| ⚠️ | High Priority |

---

## Phase 1: Research & Environment Setup
**Duration**: 1-2 weeks  
**Goal**: Validate core technical assumptions and establish build pipeline

### Android Development Environment
- [ ] 🔴 Install Android Studio Arctic Fox or later
- [ ] 🔴 Set up Android SDK (API 26-34)
- [ ] 🔴 Configure Android NDK (r25c or later)
- [ ] 🔴 Create Android Virtual Devices for testing (API 28, 33)
- [ ] 🔴 Enable USB debugging on physical test devices
- [ ] 🔴 **Deliverable**: Hello World Android app running on both devices

### Rust Android Toolchain Setup
- [x] 🟢 Install Rust via rustup
- [x] 🟢 Install cargo-ndk: `cargo install cargo-ndk`
- [x] 🟢 Add Android targets:
  - [x] 🟢 `rustup target add aarch64-linux-android`
  - [x] 🟢 `rustup target add armv7-linux-androideabi`
  - [x] 🟢 `rustup target add x86_64-linux-android`
- [x] 🟢 Configure ANDROID_NDK_HOME environment variable
- [x] 🟢 **Deliverable**: Rust "Hello from Rust" JNI call working in Android

### Critical Android API Research
- [ ] ⚠️ **Camera2 API Research**:
  - [ ] 🔴 Study CameraDevice with offscreen SurfaceTexture
  - [ ] 🔴 Test screen-off camera capture with PARTIAL_WAKE_LOCK
  - [ ] 🔴 Document `foregroundServiceType="camera"` requirements (Android 10+)
  - [ ] 🔴 **Risk Mitigation**: Confirm screen-off recording works on Android 10-15
  
- [ ] 🔴 **MediaProjection API Research**:
  - [ ] 🔴 Study system dialog requirement
  - [ ] 🔴 Test screen mirroring capture
  - [ ] 🔴 Document user consent flow requirements
  
- [ ] 🔴 **Foreground Service Research**:
  - [ ] 🔴 Study Android 11-15 foreground service type declarations
  - [ ] 🔴 Test service survival through battery optimization
  - [ ] 🔴 Research OEM-specific battery killer behaviors
  - [ ] 🔴 **Deliverable**: Research document with findings

### Networking & WebRTC Research
- [ ] 🔴 Study WebRTC P2P connection establishment
- [ ] 🔴 Research STUN/TURN server requirements for NAT traversal
- [ ] 🔴 Design QR code pairing protocol
- [ ] 🔴 **Deliverable**: Network protocol design document

### Project Structure Initialization
- [x] 🟢 Initialize Git repository
- [x] 🟢 Create .gitignore for Android/Rust projects
- [x] 🟢 **Deliverable**: Repository structure ready ✅

---

## Phase 2: Mirror Target App - Core Services
**Duration**: 3-4 weeks  
**Goal**: Build the Target app that runs on old phones as CCTV server

### Android Project Foundation
- [ ] 🔴 Create Android project with package `com.mirror.target`
- [ ] 🔴 Configure minSdk=26, targetSdk=34
- [ ] 🔴 Set up CMake/NDK integration for Rust
- [ ] 🔴 Create basic UI: Setup screen, Status screen
- [ ] 🔴 **Deliverable**: Basic app launching with Rust integration

### Foreground Service Architecture
- [x] 🟢 Create `MirrorTargetService` extending `Service`
- [x] 🟢 Declare service in AndroidManifest.xml with proper foregroundServiceType
- [x] 🟢 Implement service lifecycle: onCreate, onStartCommand, onBind
- [x] 🟢 Create persistent notification with controls
- [x] 🟢 Implement `PARTIAL_WAKE_LOCK` for CPU keep-alive
- [x] 🟢 **Deliverable**: Service runs persistently with notification

### Permission Management System
- [ ] 🔴 Implement runtime permission requests:
  - [ ] 🔴 CAMERA
  - [ ] 🔴 RECORD_AUDIO
  - [ ] 🔴 ACCESS_FINE_LOCATION
  - [ ] 🔴 ACCESS_BACKGROUND_LOCATION
  - [ ] 🔴 FOREGROUND_SERVICE types
- [ ] 🔴 Create permission onboarding flow
- [ ] 🔴 Handle "Don't ask again" scenarios
- [ ] 🔴 **Deliverable**: All permissions granted and validated

### Camera2 Implementation
- [x] 🟢 Create `CameraCaptureManager` class
- [x] 🟢 Implement camera selection (front/back toggle)
- [x] 🟢 Set up `CameraDevice` and `CameraCaptureSession`
- [x] 🟢 Configure offscreen `SurfaceTexture` (no display surface)
- [x] 🟢 Implement ImageReader for frame capture
- [x] 🟢 Add camera state machine
- [x] 🟢 Handle camera disconnections and reconnection
- [ ] 🟡 **Deliverable**: Camera captures frames with screen off (captures but doesn't send yet)

### Audio Capture Implementation
- [x] 🟢 Create `AudioCaptureManager` class
- [x] 🟢 Set up `AudioRecord` with proper configuration
- [x] 🟢 Implement audio recording loop in background thread
- [ ] 🔴 Add audio level monitoring for VU meter
- [ ] 🟡 **Deliverable**: Continuous audio capture working (captures but doesn't send yet)

### GPS Location Tracking
- [x] ⏸️ GPS skipped for now (per user request)
- [ ] ~~Create `LocationTracker` class~~
- [ ] ~~Integrate Google Play Services Location~~
- [ ] ~~Configure Fused Location Provider~~
- [ ] ~~Implement location caching for offline scenarios~~
- [ ] ~~Add geofencing capability~~
- [ ] ~~**Deliverable**: Battery-efficient location updates working~~

### Screen Mirroring (MediaProjection)
- [ ] 🔴 Create `ScreenCaptureManager` class
- [ ] 🔴 Implement MediaProjection permission request flow
- [ ] 🔴 Set up VirtualDisplay for screen capture
- [ ] 🔴 Handle screen rotation events
- [ ] 🔴 **Deliverable**: Screen capture working with user consent

### Gallery Access (Scoped Storage)
- [ ] 🔴 Create `GalleryManager` class
- [ ] 🔴 Query MediaStore for images and videos
- [ ] 🔴 Implement pagination for large galleries
- [ ] 🔴 Handle storage permissions for Android 10+
- [ ] 🔴 Create thumbnail generation
- [ ] 🔴 **Deliverable**: Gallery listing and thumbnail generation

---

## Phase 3: Mirror Core - Rust Native Library
**Duration**: 3-4 weeks  
**Goal**: Build high-performance Rust core for encoding and encryption

### Rust Project Setup
- [x] 🟢 Create `mirror-core` crate structure
- [x] 🟢 Configure Cargo for Android cross-compilation
- [x] 🟢 Set up jni crate for JNI bindings
- [x] 🟢 **Deliverable**: Rust library compiles for all Android targets (arm64-v8a, armeabi-v7a)

### Video Encoding (H.264)
- [x] ⏸️ Video encoding in Rust skipped (using Android MediaCodec instead per Claude AI advice)
- [ ] ~~Research and integrate video encoding library~~
- [ ] ~~Implement `VideoEncoder` struct~~
- [ ] ~~Implement encode_frame() function~~
- [ ] ~~Support configuration: resolution, bitrate, frame rate~~
- [ ] ~~**Deliverable**: Raw camera frames → H.264 NAL units~~

### Audio Encoding (Opus)
- [x] ⏸️ Audio encoding in Rust skipped (using Android MediaCodec instead per Claude AI advice)
- [ ] ~~Integrate `opus` crate or bind to libopus~~
- [ ] ~~Implement `AudioEncoder` struct~~
- [ ] ~~Implement encode_packet() function~~
- [ ] ~~Configure for VOIP quality (24kbps, 20ms frames)~~
- [ ] ~~**Deliverable**: Raw PCM → Opus packets~~

### End-to-End Encryption
- [x] 🟢 Integrate `ring` for cryptography
- [ ] 🟡 Implement key exchange protocol (X25519) - not yet used
- [x] 🟢 Implement AES-256-GCM encryption/decryption
- [x] 🟢 Create `CryptoSession` struct
- [ ] 🟡 **Security Review**: Verify encryption implementation
- [x] 🟢 **Deliverable**: JNI functions `nativeEncryptPacket` / `nativeDecryptPacket` working

### Stream Multiplexing
- [x] 🟢 Design packet format
- [x] 🟢 Implement `StreamMux` for combining multiple streams
- [x] 🟢 Implement `StreamDemux` for separating streams
- [x] 🟢 Handle packet fragmentation for large video frames
- [x] 🟢 **Deliverable**: JNI functions `nativeMuxPacket` / `nativeDemuxPacket` working

### JNI Bridge Layer
- [x] 🟢 Create JNI exports for Rust crypto and network modules
- [x] 🟢 Implement memory-safe JNI calls
- [x] 🟢 Create Java `RustBridge` class as facade
- [x] 🟢 **Deliverable**: Kotlin can call all Rust functions (encrypt/decrypt/mux/demux)

---

## Phase 4: Mirror Host App - Remote Client
**Duration**: 3-4 weeks  
**Goal**: Build the Host app for user to monitor Target devices

### Android Project Foundation
- [ ] 🔴 Create Android project with package `com.mirror.host`
- [ ] 🔴 Configure minSdk=26, targetSdk=34
- [ ] 🔴 Set up Material Design 3 UI components
- [ ] 🔴 Create bottom navigation
- [ ] 🔴 **Deliverable**: Basic UI structure with navigation

### Device Pairing System
- [ ] 🔴 Create pairing flow (QR code / PIN)
- [ ] 🔴 Implement secure token generation
- [ ] 🔴 Store paired devices in encrypted SharedPreferences
- [ ] 🔴 Create device management UI
- [ ] 🔴 **Deliverable**: Two devices can pair securely

### WebRTC Connection Management
- [x] ⏸️ WebRTC skipped for now (using simple TCP socket instead per Claude AI advice)
- [x] 🟢 **Alternative Deliverable**: TCP socket server on port 8080 in Target app

### Live Camera View
- [ ] 🔴 Create `LiveCameraActivity`
- [ ] 🔴 Integrate ExoPlayer or SurfaceView
- [ ] 🔴 Implement H.264 decoding
- [ ] 🔴 Add overlay controls: record, snapshot, camera switch
- [ ] 🔴 **Deliverable**: Live video stream displayed

### Audio Monitor
- [ ] 🔴 Create `AudioMonitorActivity`
- [ ] 🔴 Implement Opus audio decoding
- [ ] 🔴 Add audio playback via AudioTrack
- [ ] 🔴 Implement recording toggle
- [ ] 🔴 **Deliverable**: Live audio streaming with recording

### GPS Map Tracker
- [ ] 🔴 Integrate Google Maps SDK
- [ ] 🔴 Create `MapTrackerActivity`
- [ ] 🔴 Implement location marker updates
- [ ] 🔴 Add location history trail
- [ ] 🔴 Implement geofencing alerts
- [ ] 🔴 **Deliverable**: Real-time location tracking on map

### Screen Mirror Viewer
- [ ] 🔴 Create `ScreenMirrorActivity`
- [ ] 🔴 Implement video stream rendering
- [ ] 🔴 Add recording toggle
- [ ] 🔴 **Deliverable**: Live screen mirroring with recording

### Gallery Browser
- [ ] 🔴 Create `GalleryBrowserActivity`
- [ ] 🔴 Implement thumbnail caching
- [ ] 🔴 Add download functionality
- [ ] 🔴 Implement video playback
- [ ] 🔴 **Deliverable**: Remote gallery browsing and download

### Recording & Storage
- [ ] 🔴 Create `RecordingManager`
- [ ] 🔴 Implement MP4 muxing
- [ ] 🔴 Create recordings browser UI
- [ ] 🔴 **Deliverable**: Recordings saved and playable

---

## Phase 5: Advanced Features & Optimization
**Duration**: 2-3 weeks

### Motion Detection
- [ ] 🔴 Research motion detection algorithms
- [ ] 🔴 Implement motion detection in Rust
- [ ] 🔴 Add adaptive FPS (full fps on motion, 5fps when idle)
- [ ] 🔴 Send motion alerts to Host
- [ ] 🔴 **Deliverable**: Motion-aware streaming

### Battery Optimization
- [ ] 🔴 Implement adaptive quality based on battery level
- [ ] 🔴 Optimize wake locks (Doze aware)
- [ ] 🔴 Implement efficient buffer pools
- [ ] 🔴 **Target**: Under 10% battery drain per hour
- [ ] 🔴 **Deliverable**: Battery usage within target

### Network Resilience
- [ ] 🔴 Implement connection retry with exponential backoff
- [ ] 🔴 Add offline mode on Target
- [ ] 🔴 Implement adaptive bitrate
- [ ] 🔴 Add SMS fallback for critical alerts
- [ ] 🔴 **Deliverable**: Robust connection handling

### Security Hardening
- [ ] 🔴 Implement certificate pinning
- [ ] 🔴 Add tamper detection
- [ ] 🔴 Implement secure logging
- [ ] 🔴 Add session timeout
- [ ] 🔴 **Security Audit**: Review all encryption
- [ ] 🔴 **Deliverable**: Security hardened

### OEM Compatibility
- [ ] 🔴 Research OEM-specific battery whitelisting
- [ ] 🔴 Xiaomi: Auto-start permission
- [ ] 🔴 Huawei: Protected apps
- [ ] 🔴 OnePlus: Battery optimization
- [ ] 🔴 Samsung: Put unused apps to sleep exclusion
- [ ] 🔴 Create onboarding wizard
- [ ] 🔴 **Deliverable**: Works across major OEMs

---

## Phase 6: Testing & Quality Assurance
**Duration**: 2 weeks

### Unit Testing
- [ ] 🔴 Rust unit tests for core library
- [ ] 🔴 Kotlin unit tests for Android logic
- [ ] 🔴 Mock testing for Android components
- [ ] 🔴 **Deliverable**: >70% code coverage

### Integration Testing
- [ ] 🔴 Test Target-Host connection scenarios
- [ ] 🔴 Test reconnection after network loss
- [ ] 🔴 Test permissions denied scenarios
- [ ] 🔴 Test service lifecycle
- [ ] 🔴 **Deliverable**: Integration test suite passing

### Device Compatibility Testing
- [ ] 🔴 Test on Android 8.0 (API 26)
- [ ] 🔴 Test on Android 10 (API 29)
- [ ] 🔴 Test on Android 12 (API 31)
- [ ] 🔴 Test on Android 14 (API 34)
- [ ] 🔴 Test on low-RAM devices (1-2GB)
- [ ] 🔴 **Deliverable**: Compatibility matrix

### Performance Testing
- [ ] 🔴 Measure battery drain (Target app)
- [ ] 🔴 Measure latency (video, audio, location)
- [ ] 🔴 Measure memory usage
- [ ] 🔴 24-hour continuous streaming test
- [ ] 🔴 **Deliverable**: Performance benchmark report

### Security Testing
- [ ] 🔴 Run static analysis (MobSF, OWASP MASVS)
- [ ] 🔴 Test encryption implementation
- [ ] 🔴 Verify no hardcoded secrets
- [ ] 🔴 Check for Android vulnerabilities
- [ ] 🔴 **Deliverable**: Security assessment report

---

## Phase 7: Documentation & Deployment
**Duration**: 1-2 weeks

### User Documentation
- [ ] 🔴 Create user guide with screenshots
- [ ] 🔴 Write setup tutorial
- [ ] 🔴 Document OEM-specific battery settings
- [ ] 🔴 Create FAQ document
- [ ] 🔴 **Deliverable**: Complete user documentation

### Developer Documentation
- [ ] 🔴 Document architecture decisions
- [ ] 🔴 Write API documentation
- [ ] 🔴 Create contribution guidelines
- [ ] 🔴 Document build process
- [ ] 🔴 **Deliverable**: Developer documentation

### Build & Release
- [ ] 🔴 Set up CI/CD pipeline (GitHub Actions)
- [ ] 🔴 Create release signing configuration
- [ ] 🔴 Build release APKs (Target and Host)
- [ ] 🔴 Optimize APK size (ProGuard/R8)
- [ ] 🔴 **Deliverable**: Signed release APKs

### Distribution
- [ ] 🔴 **F-Droid**: Create metadata and submit
- [ ] 🔴 **GitHub Releases**: Create release notes
- [ ] 🔴 **Play Store**: Review policies and submit (optional)
- [ ] 🔴 **Deliverable**: App available for download

---

## Quick Stats

| Metric | Count |
|--------|-------|
| Total Tasks | 150+ |
| Completed | ~35 |
| In Progress | ~15 |
| Not Started | ~100 | |

---

## How to Use This TODO

1. **Update status** as you complete tasks (🔴 → 🟡 → 🟢)
2. **Add notes** under tasks for blockers or learnings
3. **Move completed phases** to archive section below
4. **Review weekly** and adjust priorities

## Archive (Completed Work)

### 2026-04-04 - Foundation Complete
- ✅ Git repository initialized with proper .gitignore
- ✅ Rust toolchain setup (rustc, cargo-ndk, Android targets)
- ✅ Rust mirror-core compiles for Android (arm64-v8a, armeabi-v7a)
- ✅ JNI bridge with 4 functions: encrypt/decrypt/mux/demux
- ✅ Target app: Foreground service with wake lock
- ✅ Target app: Camera2 capture manager (screen-off ready)
- ✅ Target app: Audio capture manager
- ✅ Target app: TCP server on port 8080
- ✅ Host app: Fixed navigation (activity-based)
- ✅ GPS stripped from project scope
