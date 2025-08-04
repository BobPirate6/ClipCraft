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

# Сохраняем все Retrofit интерфейсы
-keep interface com.example.clipcraft.data.remote.** { *; }
-keep class com.example.clipcraft.data.remote.** { *; }

# Сохраняем методы с аннотациями Retrofit
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Сохраняем generic типы для Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

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

# Сохраняем suspend функции
-keepclassmembers class * {
    suspend <methods>;
}
-keep class kotlin.coroutines.jvm.internal.** { *; }
-keep class kotlin.coroutines.SafeContinuation { *; }

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
# ВРЕМЕННО ЗАКОММЕНТИРОВАНО ДЛЯ ОТЛАДКИ
# -assumenosideeffects class android.util.Log {
#     public static boolean isLoggable(java.lang.String, int);
#     public static int v(...);
#     public static int i(...);
#     public static int w(...);
#     public static int d(...);
#     public static int e(...);
# }

# === Media3 Transformer и видео обработка ===
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.**

# === MediaCodec и видео обработка ===
-keep class android.media.** { *; }
-keep class * implements android.media.MediaCodec$Callback { *; }
-keep class * extends android.media.MediaCodecInfo { *; }

# === Services и Workers ===
-keep class com.example.clipcraft.services.** { *; }
-keep class com.example.clipcraft.workers.** { *; }
-keep class com.example.clipcraft.domain.** { *; }
-keep class com.example.clipcraft.data.** { *; }
-keep class com.example.clipcraft.ui.** { *; }

# === Нативные методы ===
-keepclasseswithmembernames class * {
    native <methods>;
}

# === Классы для рефлексии ===
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# === Coil (для загрузки изображений) ===
-keep class coil.** { *; }
-dontwarn coil.**

# === Транскрипция и аудио обработка ===
-keep class com.example.clipcraft.services.TranscriptionService { *; }
-keep class com.example.clipcraft.services.AudioExtractorService { *; }
-keep class com.example.clipcraft.services.VideoAnalyzerService { *; }

# === Сетевые запросы и API ===
-keepclassmembers class com.example.clipcraft.data.remote.** {
    <fields>;
    <methods>;
}
-keep class com.example.clipcraft.models.api.** { *; }

# === Предотвращаем удаление enum'ов ===
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# === Сохраняем Parcelable ===
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# === Сохраняем Serializable ===
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# === Room Database ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    *;
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