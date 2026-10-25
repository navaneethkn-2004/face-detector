# Signed Release Build Guide

This guide describes how to sign and distribute a release APK or Android App Bundle (AAB) for **Face Detector**.

> [!CAUTION]
> **Never commit your `.keystore`, `.jks` file, or keystore passwords to version control.** Store your production keystore securely in a password manager or CI/CD secret vault.

---

## Step 1: Generate a Release Keystore

If you do not already have an upload or release keystore, generate one using the Java `keytool` CLI:

```bash
keytool -genkeypair -v \
  -keystore release-key.jks \
  -alias face-detector-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storetype JKS
```

You will be prompted to provide:
- Keystore password
- Personal / organizational details (CN, OU, O, L, ST, C)
- Key password (or confirm keystore password)

Keep the generated `release-key.jks` in a secure location outside the repository.

---

## Step 2: Configure Signing in `app/build.gradle.kts`

To keep credentials secure and outside version control, reference them using environment variables or user-level `gradle.properties` (`~/.gradle/gradle.properties`):

### Add to `~/.gradle/gradle.properties` (or CI environment variables):
```properties
FACE_KEYSTORE_PATH=/path/to/release-key.jks
FACE_KEYSTORE_PASSWORD=your_keystore_password
FACE_KEY_ALIAS=face-detector-key
FACE_KEY_PASSWORD=your_key_password
```

### In `app/build.gradle.kts`:
```kotlin
android {
    ...
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("FACE_KEYSTORE_PATH") 
                ?: (project.findProperty("FACE_KEYSTORE_PATH") as? String)
            val storePassword = System.getenv("FACE_KEYSTORE_PASSWORD") 
                ?: (project.findProperty("FACE_KEYSTORE_PASSWORD") as? String)
            val keyAlias = System.getenv("FACE_KEY_ALIAS") 
                ?: (project.findProperty("FACE_KEY_ALIAS") as? String)
            val keyPassword = System.getenv("FACE_KEY_PASSWORD") 
                ?: (project.findProperty("FACE_KEY_PASSWORD") as? String)

            if (keystorePath != null && storePassword != null && keyAlias != null && keyPassword != null) {
                storeFile = file(keystorePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## Step 3: Build the Signed Release APK

Run the release assembly task:

```bash
./gradlew assembleRelease
```

The signed release APK will be generated at:
```
app/build/outputs/apk/release/app-release.apk
```

---

## Step 4: Alternative: Manual Signing with `apksigner`

If you build an unsigned release APK with `./gradlew assembleRelease`, sign it manually using Android SDK build-tools:

1. **Align the APK** using `zipalign`:
   ```bash
   zipalign -v -p 4 app/build/outputs/apk/release/app-release-unsigned.apk app-release-aligned.apk
   ```

2. **Sign the APK** using `apksigner`:
   ```bash
   apksigner sign --ks release-key.jks \
     --ks-key-alias face-detector-key \
     --out app-release-signed.apk \
     app-release-aligned.apk
   ```

3. **Verify the signature**:
   ```bash
   apksigner verify --verbose app-release-signed.apk
   ```

Expected output:
```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

The resulting `app-release-signed.apk` is ready for direct installation or distribution on the Google Play Store.
