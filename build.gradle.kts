// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48")
        classpath("com.google.gms:google-services:4.4.2")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")  // Добавьте эту строку
    }
}

plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false

}