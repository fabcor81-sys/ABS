# Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.ui.** { *; }

# Room Database
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Kotlin Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    *** kotlinx.serialization.serializer(...);
}

# General
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
