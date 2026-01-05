# Testing Guide - Pure Android HookApp Implementation

## Test Files Overview

All test files are located in:
- **Unit Tests:** `relay-client-android/app/src/test/kotlin/com/relay/pure/`
- **Integration Tests:** `relay-client-android/app/src/androidTest/kotlin/com/relay/pure/`

## Unit Tests

### 1. TranspilerTests.kt
Tests transpiler components without device/emulator needed.

```bash
# Run all transpiler tests
./gradlew test -Dtest.single=TranspilerTests

# Run specific test class
./gradlew test -Dtest.single=TranspileErrorTest
./gradlew test -Dtest.single=TranspileOptionsTest
./gradlew test -Dtest.single=TranspileErrorMapperTest
./gradlew test -Dtest.single=JsxTranspilerCacheTest
```

**What's tested:**
- ✅ Error type creation and context
- ✅ Options validation (filename, extension, etc)
- ✅ Error mapping from JNI format
- ✅ Cache hit/miss/eviction/expiration

### 2. ImportManagerTest.kt
Tests import management logic.

```bash
./gradlew test -Dtest.single=ImportManagerTest
```

**What's tested:**
- ✅ Import extraction patterns
- ✅ Cache operations (size, clear)
- ✅ Import name tracking

### 3. StylingTests.kt
Tests styling components.

```bash
./gradlew test -Dtest.single=ThemedStylerBridgeTest
./gradlew test -Dtest.single=AndroidStylesTest
```

**What's tested:**
- ✅ Style application API
- ✅ Theme retrieval
- ✅ Error handling (empty selector, etc)
- ✅ Data class creation

### 4. HookContextTest.kt
Tests hook execution context.

```bash
./gradlew test -Dtest.single=HookContextTest
./gradlew test -Dtest.single=JsxElementTest
```

**What's tested:**
- ✅ Context creation with imports
- ✅ Hook execution flow
- ✅ Callback invocation
- ✅ JsxElement structure (nested, text, props)

## Integration Tests

Integration tests require Android device or emulator.

### HookRendererIntegrationTests.kt

```bash
# Run all integration tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest -Dtest.single=HookRendererIntegrationTest
./gradlew connectedAndroidTest -Dtest.single=TranspilerIntegrationTest
./gradlew connectedAndroidTest -Dtest.single=StylingIntegrationTest
```

**What's tested:**
- ✅ Full rendering pipeline (transpile → prefetch → execute)
- ✅ Cache consistency across operations
- ✅ Import deduplication behavior
- ✅ Error propagation through layers
- ✅ Multi-hook sequential execution
- ✅ Styling with real library (if loaded)

## Running All Tests

```bash
# Unit tests (no device needed)
./gradlew test

# Integration tests (requires device/emulator)
./gradlew connectedAndroidTest

# Both
./gradlew test connectedAndroidTest

# With coverage
./gradlew test connectedAndroidTest jacocoTestReport
```

## Setting Up Test Environment

### Prerequisites

1. **Android SDK & NDK**
   ```bash
   # Check SDK is installed
   ls $ANDROID_HOME/platforms/
   
   # Check NDK is installed
   ls $ANDROID_HOME/ndk/26.1.10909125/  # or latest version
   ```

2. **Gradle Wrapper**
   ```bash
   cd relay-client-android
   ./gradlew --version  # Should work
   ```

3. **Device/Emulator (for integration tests)**
   ```bash
   # List connected devices
   adb devices
   
   # Or start emulator
   emulator -avd Pixel_4_API_31 &
   ```

### Building Rust Libraries

Before running tests, build the native libraries:

```bash
# Build hook-transpiler
cd /home/ari/dev/hook-transpiler
bash build-and-deploy.sh

# Build themed-styler
cd /home/ari/dev/themed-styler
npm install
npm run build

# Copy to relay-client-android
cp hook-transpiler/target/aarch64-linux-android/release/librelay_hook_transpiler.so \
   relay-client-android/app/src/main/jniLibs/arm64-v8a/

cp themed-styler/target/aarch64-linux-android/release/librelay_styled_styler.so \
   relay-client-android/app/src/main/jniLibs/arm64-v8a/
```

## Test Output Examples

### Successful Unit Test Run

```
> Task :app:test
TranspilerTests PASSED
ImportManagerTest PASSED
StylingTests PASSED
HookContextTest PASSED

BUILD SUCCESSFUL in 12s
```

### Successful Integration Test Run

```
> Task :app:connectedAndroidTest
HookRendererIntegrationTest PASSED
TranspilerIntegrationTest PASSED
StylingIntegrationTest PASSED

BUILD SUCCESSFUL in 45s
```

## Debugging Tests

### Enable Logging

```bash
# Set log level
./gradlew test -Dorg.gradle.logging.level=debug

# Run with output
./gradlew test --info
```

### Test Specific Scenario

```bash
# Run single test method
./gradlew test --tests=JsxTranspilerCacheTest.cacheEvictionWhenFull

# Run with grep pattern
./gradlew test --tests=*Cache*
```

### Debug on Device

```bash
# Start logcat monitoring
adb logcat | grep "TranspilerBridge\|ImportManager\|ThemedStylerBridge"

# Run test and watch logs
./gradlew connectedAndroidTest
```

## Test Coverage

Generate test coverage report:

```bash
# Install Jacoco plugin (in build.gradle)
plugins {
    id("jacoco")
}

# Generate coverage
./gradlew test jacocoTestReport

# Open report
open app/build/reports/jacoco/test/html/index.html
```

## Troubleshooting

### Test Fails: "Library not loaded"

**Cause:** Native libraries (.so files) not found

**Solution:**
```bash
# Build and copy libraries
bash /home/ari/dev/hook-transpiler/build-and-deploy.sh
npm --prefix /home/ari/dev/themed-styler run build

# Check they exist
ls relay-client-android/app/src/main/jniLibs/arm64-v8a/
# Should show: librelay_hook_transpiler.so, librelay_styled_styler.so
```

### Test Fails: "Cannot find HTTP server"

**Cause:** Mock server not running (for integration tests that hit localhost:8083)

**Solution:**
```bash
# Start mock server in another terminal
cd /home/ari/dev/hook-transpiler/tests/web
npm run start  # Runs on port 8083

# Then run tests
./gradlew connectedAndroidTest
```

### Test Hangs on Import Prefetch

**Cause:** HTTP request timeout waiting for server

**Solution:**
- Verify mock server is running: `curl http://localhost:8083/`
- Check device can reach host: `adb shell ping 10.0.2.2`  (for emulator)
- Increase timeout in ImportManager (currently 30s)

### Device Not Found

**Cause:** No device/emulator connected

**Solution:**
```bash
# List devices
adb devices

# Start emulator
emulator -avd Pixel_4_API_31 &

# Wait for it to boot
adb wait-for-device

# Verify
adb devices -l
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Test Pure Android Implementation

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 17
      
      - name: Build Rust libraries
        run: |
          cd hook-transpiler
          bash build-and-deploy.sh
      
      - name: Run unit tests
        run: |
          cd relay-client-android
          ./gradlew test
      
      - name: Generate coverage
        run: |
          cd relay-client-android
          ./gradlew test jacocoTestReport
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

## Performance Testing

### Benchmark Tests

```kotlin
@Test
fun benchmarkTranspilation() {
    val source = "const App = () => <div/>"
    val start = System.currentTimeMillis()
    
    repeat(100) {
        TranspilerBridge.transpile(source, "test.jsx")
    }
    
    val elapsed = System.currentTimeMillis() - start
    println("100 transpilations: ${elapsed}ms (avg: ${elapsed/100}ms)")
    // Should be: cache hit ~1ms, cache miss ~50ms
}
```

### Run Benchmarks

```bash
./gradlew test --tests=*Benchmark*
```

## Validation Checklist

- [ ] Unit tests pass: `./gradlew test`
- [ ] Can start emulator: `emulator -avd Pixel_4_API_31 &`
- [ ] Device connected: `adb devices` shows device
- [ ] Libraries built: `ls app/src/main/jniLibs/arm64-v8a/librelay_*`
- [ ] Integration tests pass: `./gradlew connectedAndroidTest`
- [ ] Coverage > 80%: `./gradlew jacocoTestReport`
- [ ] All test classes found: Check test/kotlin and androidTest/kotlin
- [ ] No flaky tests: Run tests multiple times

## Test Files Checklist

- [x] TranspilerTests.kt (150 LOC) - 5 test classes, 25+ test methods
- [x] ImportManagerTest.kt (80 LOC) - 1 test class, 8 test methods
- [x] StylingTests.kt (100 LOC) - 2 test classes, 10 test methods
- [x] HookContextTest.kt (120 LOC) - 2 test classes, 10 test methods
- [x] HookRendererIntegrationTests.kt (280 LOC) - 3 test classes, 15+ test methods

**Total: 730 LOC, 60+ test methods**

## Next: Running Tests

```bash
cd /home/ari/dev/relay-client-android

# Run all unit tests
./gradlew test

# Run all tests (unit + integration)
./gradlew test connectedAndroidTest

# Watch for failures
./gradlew test --watch
```
