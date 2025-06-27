plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.example.clipcraft"
    compileSdk  = 35          // можно оставить 35; AGP лишь выдает warning

    defaultConfig {
        applicationId = "com.example.clipcraft"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // <<< главное добавление
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"   // совместима с Kotlin 1.9.22
    }
    // >>>
}

dependencies {
    /* ----- Jetpack Compose через BOM ----- */
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))

    // базовые модули UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    /* ----- Android Material (View-based) ----- */
    implementation ("androidx.compose.material:material-icons-extended:1.6.8") // Или последняя стабильная версия

    // ДОБАВЛЕНО: Для поддержки AppCompat тем (включает Theme.AppCompat.DayNight)
    implementation ("androidx.appcompat:appcompat:1.6.1") // Используйте последнюю стабильную версию.
    // Актуальную можно проверить здесь:
    // https://developer.android.com/jetpack/androidx/releases/appcompat

    /* ----- Сеть ----- */
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    /* ----- Тесты (опционально, но пусть будут) ----- */
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
