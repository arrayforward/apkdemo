# Default ProGuard rules for release builds.
# Debug builds do not use these.

# Keep our app classes and entry points.
-keep class com.anomaly.goldiesettings.** { *; }

# OkHttp.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Material/AndroidX standard rules.
-keep class com.google.android.material.** { *; }
-keepclassmembers class * extends androidx.fragment.app.Fragment { *; }
