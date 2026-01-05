#!/bin/bash
# Auto-increment version and build/deploy APK to connected device

set -e

cd "$(dirname "$0")/android"

# Get current version code and name from build.gradle
CURRENT_VERSION_CODE=$(grep -oP 'versionCode \K\d+' app/build.gradle)
CURRENT_VERSION_NAME=$(grep -oP "versionName \"\K[^\"]*" app/build.gradle)

# Increment version code
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

# Parse and increment version name (assumes format X.Y.Z)
IFS='.' read -r -a VERSION_PARTS <<< "$CURRENT_VERSION_NAME"
MAJOR="${VERSION_PARTS[0]}"
MINOR="${VERSION_PARTS[1]}"
PATCH="${VERSION_PARTS[2]}"
NEW_PATCH=$((PATCH + 1))
NEW_VERSION_NAME="${MAJOR}.${MINOR}.${NEW_PATCH}"

echo "Current version: $CURRENT_VERSION_CODE ($CURRENT_VERSION_NAME)"
echo "New version: $NEW_VERSION_CODE ($NEW_VERSION_NAME)"

# Update build.gradle
sed -i "s/versionCode $CURRENT_VERSION_CODE/versionCode $NEW_VERSION_CODE/" app/build.gradle
sed -i "s/versionName \"$CURRENT_VERSION_NAME\"/versionName \"$NEW_VERSION_NAME\"/" app/build.gradle

echo "Updated build.gradle"

# Build APK
echo "Building APK..."
./gradlew clean assembleDebug

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "No device connected. APK built but not installed."
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    exit 0
fi

# Install to device
echo "Installing to device..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Build and deployment complete! Version: $NEW_VERSION_NAME ($NEW_VERSION_CODE)"
