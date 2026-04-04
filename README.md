# Mirror - Android Surveillance System

Transform your old Android phone into a CCTV camera with live streaming.

## 📱 Two Apps

| App | Install On | Purpose | Icon |
|-----|-----------|---------|------|
| **Mirror** | Your main phone | View/Control the camera | 🔵 Blue |
| **Mirror Me** | Old phone (CCTV) | Stream camera & audio | 🟢 Green |

## ✨ Features

- **Live Video** - H.264 encoded camera streaming
- **Live Audio** - Real-time microphone streaming  
- **GPS Tracking** - Location sharing on map
- **Cross-Network** - Works on same WiFi or different networks via WebRTC
- **Encrypted** - AES-256-GCM end-to-end encryption

## 🚀 Quick Start

### Same WiFi (Fastest)
1. Install **Mirror Me** on old phone → Tap "Start"
2. Note the IP address shown
3. Install **Mirror** on your phone → Enter IP → Connect

### Different Networks
1. Both phones need internet
2. **Mirror** app → "Connect via WebRTC"
3. Scan QR codes between devices
4. Direct P2P connection established!

## 🏗️ Build

```bash
# Build Mirror (Host)
cd mirror-host && ./gradlew assembleDebug

# Build Mirror Me (Target)
cd mirror-target && ./gradlew assembleDebug
```

## 📂 Project Structure

```
mirror/
├── mirror-host/       # Mirror app (viewer)
├── mirror-target/     # Mirror Me app (camera)
├── mirror-core/       # Rust crypto library
└── mirror-final/      # Built APKs
```

## 📄 License

MIT License - See [LICENSE](LICENSE) file

---

Made with ❤️ for privacy-focused home surveillance
