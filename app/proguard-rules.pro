# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Сохраняем информацию о строках для отладки (можно убрать для production)
-keepattributes SourceFile,LineNumberTable

# Скрываем оригинальное имя файла
-renamesourcefileattribute SourceFile

# === Retrofit ===
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# === OkHttp ===
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# === Gson ===
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Сохраняем модели данных
-keep class com.example.clipcraft.models.** { *; }

# === Firebase ===
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# === Hilt ===
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

-keepclasseswithmembers class * {
    @dagger.hilt.* <fields>;
}

-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}

-keepclasseswithmembers class * {
    @javax.inject.* <methods>;
}

# === Media3/ExoPlayer ===
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# === Compose ===
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# === Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# === WorkManager ===
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class com.example.clipcraft.workers.** { *; }

# === Сохраняем классы сервисов ===
-keep class com.example.clipcraft.services.** { *; }
-keep class com.example.clipcraft.data.remote.** { *; }

# === Убираем логи в release ===
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# === Убираем отладочную информацию ===
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(...);
    static void checkNotNullParameter(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkNotNullExpressionValue(...);
    static void checkReturnedValueIsNotNull(...);
    static void checkFieldIsNotNull(...);
    static void throwUninitializedPropertyAccessException(...);
}