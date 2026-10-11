# BluePilot Remote v3 — R8/ProGuard rules (Performance section).
# Minification + resource shrinking are ON for release builds.

# ----- Kotlin / coroutines -----
-dontwarn kotlinx.coroutines.**

# ----- kotlinx-serialization -----
# Keep serializers for our @Serializable models (layouts, macros, gamepads, skins).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.bluepilot.remote.**$$serializer { *; }
-keepclassmembers class com.bluepilot.remote.** { *** Companion; }
-keepclasseswithmembers class com.bluepilot.remote.** { kotlinx.serialization.KSerializer serializer(...); }

# ----- Timber -----
-dontwarn org.jetbrains.annotations.**

# ----- Room: keep entity column names stable -----
-keep class com.bluepilot.remote.data.db.*Entity { *; }

# Hilt / Compose / Room generate their own keep rules via AAR consumer rules.
