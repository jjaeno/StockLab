/**
 * StockLab Android App - Gradle 빌드 설정
 *
 * 주요 라이브러리:
 * - Jetpack Compose (UI)
 * - Retrofit + OkHttp (네트워킹)
 * - Firebase Auth (인증)
 * - Hilt (의존성 주입)
 * - Coil (이미지 로딩)
 * - MPAndroidChart (차트)
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 백엔드 API URL 설정
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/api/v1/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "http://10.0.2.2:8080/api/v1/", "\"https://your-production-url.com/api/v1/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ===== AndroidX Core =====
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ===== Jetpack Compose =====
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ===== Navigation Compose =====
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ===== Hilt (Dependency Injection) =====
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")

    // ===== Retrofit + OkHttp (네트워킹) =====
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ===== Firebase =====
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ==== Accompanist (SwipeRefresh) ====
    implementation("com.google.accompanist:accompanist-swiperefresh:0.34.0")
    // ===== Coil (이미지 로딩) =====
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ===== MPAndroidChart (차트) =====
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ===== DataStore (로컬 저장소) =====
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ===== Accompanist (시스템 UI 제어) =====
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // ===== Gson =====
    implementation("com.google.code.gson:gson:2.10.1")

    // ===== Coroutines =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // ===== Testing =====
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Hilt를 위한 kapt 설정
kapt {
    correctErrorTypes = true
}