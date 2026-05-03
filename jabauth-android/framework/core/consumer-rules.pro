# JABAuth Core - Consumer ProGuard Rules

# Keep all public API
-keep public class com.jabauth.core.** { public *; }

# Keep security crypto classes (required for EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class androidx.security.crypto.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
