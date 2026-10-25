# ML Kit Face Detection keep rules
-keep class com.google.mlkit.vision.face.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }

# CameraX keep rules
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }

# Compose & Coroutines
-keep class androidx.compose.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
