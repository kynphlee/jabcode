# Device Installation Guide - Phase 3 Testing

**Quick Reference for Installing and Testing the Diagnostic App**

---

## Prerequisites

### 1. Enable Developer Mode on Android Device
1. Settings → About Phone
2. Tap "Build Number" 7 times
3. Enter PIN/password if prompted
4. "Developer mode enabled" message appears

### 2. Enable USB Debugging
1. Settings → System → Developer Options
2. Toggle "USB Debugging" ON
3. Connect device via USB
4. Approve "Allow USB debugging?" prompt on device

### 3. Verify ADB Connection
```bash
adb devices
```

**Expected Output:**
```
List of devices attached
XXXXXXXXXX    device
```

If device shows as "unauthorized", check device for approval prompt.

---

## Build and Install

### Option 1: Direct Install (Recommended)
```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode/jabauth-android
./gradlew :diagnostic-app:installDebug
```

**Expected Output:**
```
BUILD SUCCESSFUL in Xs
```

### Option 2: Build Then Install
```bash
# Build APK
./gradlew :diagnostic-app:assembleDebug

# Locate APK
ls -lh diagnostic-app/build/outputs/apk/debug/

# Install manually
adb install -r diagnostic-app/build/outputs/apk/debug/diagnostic-app-debug.apk
```

### Option 3: Install via Android Studio
1. Open project in Android Studio
2. Select device from dropdown
3. Click "Run" (green play button)

---

## Launch App

### Via ADB
```bash
adb shell am start -n com.jabauth.diagnostic/.MainActivity
```

### Via Device
1. Open app drawer
2. Find "JABCode Diagnostic" app
3. Tap to launch

---

## Monitor Logs

### Basic Monitoring
```bash
# All diagnostic app logs
adb logcat | grep -E "jabauth|ScannerViewModel|DiagnosticLogger"

# Specific component
adb logcat | grep "ScannerViewModel"

# Errors only
adb logcat *:E | grep "jabauth"
```

### Save Logs to File
```bash
# Continuous logging
adb logcat | grep "jabauth" > phase3_test_logs.txt

# Clear and start fresh
adb logcat -c  # Clear buffer
adb logcat | grep "jabauth" | tee phase3_test_logs.txt
```

### Filter by Log Level
```bash
# DEBUG and above
adb logcat *:D | grep "ScannerViewModel"

# INFO and above
adb logcat *:I | grep "jabauth"

# WARN and ERROR only
adb logcat *:W | grep "jabauth"
```

---

## Common Issues

### Issue: "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
**Cause:** Existing app signed with different key

**Solution:**
```bash
# Uninstall existing app
adb uninstall com.jabauth.diagnostic

# Reinstall
./gradlew :diagnostic-app:installDebug
```

### Issue: Device Not Detected
**Cause:** USB debugging not authorized

**Solutions:**
1. Check device for authorization prompt
2. Revoke USB debugging authorizations:
   - Settings → Developer Options → Revoke USB debugging authorizations
3. Reconnect device and approve again
4. Try different USB cable/port

### Issue: Build Failed
**Cause:** Various (check error message)

**Solutions:**
```bash
# Clean build
./gradlew clean :diagnostic-app:assembleDebug

# Sync dependencies
./gradlew --refresh-dependencies :diagnostic-app:assembleDebug

# Check Java version (should be 23.0.1)
java -version
```

### Issue: App Crashes on Launch
**Cause:** Check logcat for stack trace

**Solution:**
```bash
# Capture crash log
adb logcat -d *:E | grep -A 50 "jabauth" > crash_log.txt

# View crash details
cat crash_log.txt
```

---

## Testing Workflow

### 1. Install Fresh Build
```bash
adb uninstall com.jabauth.diagnostic
./gradlew :diagnostic-app:installDebug
```

### 2. Start Log Monitoring
```bash
adb logcat -c  # Clear old logs
adb logcat | grep "jabauth" | tee test_session_$(date +%Y%m%d_%H%M%S).log
```

### 3. Enable Debug Logging
1. Launch app
2. Navigate to Settings
3. Toggle "Debug Logging" ON

### 4. Run Test Cases
- Follow PHASE3_DEVICE_TESTING.md test plan
- Document results in PHASE3_TEST_REPORT.md
- Capture relevant log excerpts

### 5. Collect Test Artifacts
```bash
# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./screenshots/

# Pull app data (if needed)
adb backup -f backup.ab -noapk com.jabauth.diagnostic

# Save final logs
adb logcat -d | grep "jabauth" > final_session_log.txt
```

---

## Quick Commands Reference

### App Management
```bash
# Install
./gradlew :diagnostic-app:installDebug

# Uninstall
adb uninstall com.jabauth.diagnostic

# Launch
adb shell am start -n com.jabauth.diagnostic/.MainActivity

# Force stop
adb shell am force-stop com.jabauth.diagnostic

# Clear data
adb shell pm clear com.jabauth.diagnostic
```

### Log Monitoring
```bash
# Live logs
adb logcat | grep "ScannerViewModel"

# Save logs
adb logcat -d > full_logcat.txt

# Clear logs
adb logcat -c

# Filter by tag
adb logcat -s ScannerViewModel:D
```

### Device Info
```bash
# Device model
adb shell getprop ro.product.model

# Android version
adb shell getprop ro.build.version.release

# Device specs
adb shell getprop | grep "product\|build"
```

### Performance Monitoring
```bash
# CPU usage
adb shell top | grep "jabauth"

# Memory usage
adb shell dumpsys meminfo com.jabauth.diagnostic

# Battery stats
adb shell dumpsys batterystats com.jabauth.diagnostic
```

---

## Test JABCode Preparation

### Generate Test Codes (if needed)
Using the encoder in the jabauth-android project:

```bash
cd /mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode

# Generate various color modes
# (Commands depend on encoder implementation)
```

### Print Test Codes
1. Generate JABCode images
2. Print on high-quality printer
3. Use matte finish paper for best results
4. Ensure codes are sharp and clear

### Display on Screen
1. Open JABCode images on computer
2. Display at full screen
3. Adjust brightness (moderate, not max)
4. Avoid screen glare

---

## Post-Testing Cleanup

### Save Test Results
```bash
# Create results directory
mkdir -p test-results/phase3_$(date +%Y%m%d)

# Save logs
cp *.log test-results/phase3_$(date +%Y%m%d)/

# Save screenshots
cp screenshots/* test-results/phase3_$(date +%Y%m%d)/

# Compress results
tar -czf phase3_results_$(date +%Y%m%d).tar.gz test-results/phase3_$(date +%Y%m%d)/
```

### Update Documentation
1. Complete PHASE3_TEST_REPORT.md with results
2. Update PROGRESS_NARRATIVE.md with findings
3. File any issues discovered
4. Document optimal settings found

---

## Troubleshooting

### ADB Not Found
```bash
# Add to PATH (Ubuntu/Linux)
export PATH=$PATH:~/Android/Sdk/platform-tools

# Verify
which adb
```

### Multiple Devices Connected
```bash
# List devices
adb devices

# Target specific device
adb -s DEVICE_SERIAL install app.apk
adb -s DEVICE_SERIAL logcat
```

### Permission Denied Errors
```bash
# Restart ADB server as root
adb kill-server
sudo adb start-server
```

---

**JARVIS**  
*Installation Guide Author*  
*Created: 2026-05-09 14:35 EDT*
