#!/bin/bash

# Build Script for Mirror Apps
# This script builds both APKs and copies them to mirror-final/

set -e

echo "=========================================="
echo "Building Mirror Apps"
echo "=========================================="
echo ""

# Create output directory
mkdir -p mirror-final

# Build Mirror (Host) App
echo "📱 Building Mirror (Host) app..."
cd mirror-host

if [ -f "./gradlew" ]; then
    ./gradlew assembleDebug
else
    gradle assembleDebug
fi

cp app/build/outputs/apk/debug/app-debug.apk ../mirror-final/Mirror.apk
echo "✅ Mirror.apk created"
cd ..

# Build Mirror Me (Target) App
echo ""
echo "📹 Building Mirror Me (Target) app..."
cd mirror-target

if [ -f "./gradlew" ]; then
    ./gradlew assembleDebug
else
    gradle assembleDebug
fi

cp app/build/outputs/apk/debug/app-debug.apk ../mirror-final/Mirror-Me.apk
echo "✅ Mirror-Me.apk created"
cd ..

# Show results
echo ""
echo "=========================================="
echo "Build Complete! 🎉"
echo "=========================================="
echo ""
echo "APK Files:"
ls -lh mirror-final/*.apk
echo ""
echo "Install instructions:"
echo "  1. Mirror-Me.apk → Install on old phone (CCTV camera)"
echo "  2. Mirror.apk → Install on your main phone (viewer)"
echo ""
echo "See mirror-final/README.md for usage instructions"
