plugins {
    id("com.android.application")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.clipcraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.clipcraft"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        
        // Дефолтный network security config для debug
        manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Подпись для release будет настроена отдельно
            
            // Используем безопасный network security config для продакшн
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_prod"
        }
        
        // Создаем отдельный buildType для альфа-тестирования
        create("alpha") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Используем безопасный network security config для альфа
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_prod"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Запись для OpenCV больше не нужна
    }
}

dependencies {
    // ─── Icons ─────────────────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.material:material-icons-extended")
       

    // ─── Kotlin ────────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Для загрузки превью видео
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")

// Для работы с разрешениями (уже есть)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // ─── Core Android ──────────────────────────────────────────────────────────
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-compose:1.10.1")


    // ─── Compose BOM ───────────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ─── Navigation ────────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ─── ViewModel ─────────────────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // ─── Hilt DI ───────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    ksp("com.google.dagger:hilt-compiler:2.48")

    // ─── Media3 (обработка и воспроизведение видео) ───────────────────────────
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("androidx.media3:media3-ui-compose:1.7.1")
    implementation("androidx.media3:media3-transformer:1.7.1")
    implementation("androidx.media3:media3-effect:1.7.1")
    implementation("androidx.media3:media3-common:1.7.1")

    // ─── Networking (Retrofit + OkHttp) ────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ─── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager для фоновых задач
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // Hilt-WorkManager интеграция
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Splash Screen API (если будете добавлять)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Gson (для MainViewModel)
    implementation("com.google.code.gson:gson:2.13.1")

    // ─── Coil (загрузка изображений/превью) ───────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ─── Permission Handling ───────────────────────────────────────────────────
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // ─── Splash screen ───────────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ─── Firebase ──────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Sign‑In
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ─── Testing ───────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
