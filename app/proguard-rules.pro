# RenoTube ProGuard / R8 Optimization Rules

# Preserve class and method names to ensure zero reflection breakage on InnerTube/Gson/JsonPath
-dontobfuscate

# Keep line numbers, generic signatures, and annotations for accurate crash stack traces on TV
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { *; }

# Keep RenoTube application, models, diagnostics, and repositories
-keep class com.renotube.tv.** { *; }
-dontwarn com.renotube.tv.**

# Keep SmartTube core backend and InnerTube models
-keep class com.liskovsoft.** { *; }
-dontwarn com.liskovsoft.**

# Keep Jayway JsonPath and JSON parsing engines
-keep class com.jayway.jsonpath.** { *; }
-dontwarn com.jayway.jsonpath.**
-keep class net.minidev.** { *; }
-dontwarn net.minidev.**
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

# Keep J2V8 JavaScript engine and Cronet
-keep class com.eclipsesource.v8.** { *; }
-dontwarn com.eclipsesource.v8.**
-keep class com.eclipsesource.j2v8.** { *; }
-dontwarn com.eclipsesource.j2v8.**
-keep class org.chromium.** { *; }
-dontwarn org.chromium.**
-keep class com.google.net.cronet.** { *; }
-dontwarn com.google.net.cronet.**

# Keep Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Retrofit and Gson serialization models
-keepclassmembers enum * { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath <fields>;
    @com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPathObj <fields>;
    @com.liskovsoft.googlecommon.common.converters.jsonpath.WithJsonPath <fields>;
}

# Keep Kotlin coroutines and reflection
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Keep Hilt / Dagger dependency injection
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# RxJava 2
-dontwarn io.reactivex.**
-keep class io.reactivex.** { *; }

# Logging and DNS dependencies
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**
-dontwarn sun.net.spi.nameservice.NameService
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
-dontwarn org.xbill.DNS.**
