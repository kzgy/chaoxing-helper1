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

    // 解决 YukiHookAPI 报错：显式开启 BuildConfig 生成
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
    compileOnly("com.highcapable.yukihookapi:api:1.2.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.2.1")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
}
