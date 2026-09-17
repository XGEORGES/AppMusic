# ProGuard / R8 Rules for Aura Music

# NewPipe Extractor & Rhino
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**

# Media3
-keep class androidx.media3.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Criptografía
-keepclassmembers class * extends java.security.Provider { *; }
-keep class com.aura.music.core.license.LicenseValidator {
    public java.lang.String validateSerial(java.lang.String, java.lang.String);
}

# DJ Aura & Gemini Models
-keep class com.aura.music.data.gemini.** { *; }

