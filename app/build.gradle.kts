plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.rush.chaoxing"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rush.chaoxing"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // YukiHookAPI 核心库（必须打包进 APK）
    implementation("com.highcapable.yukihookapi:api:1.2.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.2.1")

    // Xposed 官方基础 API（由手机框架提供，仅编译期需要）
    compileOnly("de.robv.android.xposed:api:82")

    // AndroidX 注解库（解决 Unresolved reference: Keep）
    implementation("androidx.annotation:annotation:1.7.1")

    // OkHttp 引用
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
}
