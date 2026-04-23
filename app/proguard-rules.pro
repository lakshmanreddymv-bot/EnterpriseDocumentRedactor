# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ML Kit — entity extraction, text recognition, document scanner
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Hilt — required for injection to survive shrinking
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Room — keep generated DAOs and entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Suppress notes about reflection used by Hilt/Room KSP generated code
-dontnote dagger.hilt.**
-dontnote androidx.room.**