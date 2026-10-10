plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ytdl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ytdl"
        minSdk = 29
        targetSdk = 34
        versionCode = 5
        versionName = "4.5"
    }

    // 3 ملفات APK:
    //  - arm64-v8a      : معظم الموبايلات الحديثة (أصغر حجم)
    //  - armeabi-v7a    : الموبايلات اللي نظامها 32-bit (زي بعض موبايلات Samsung A)
    //  - universal      : يشتغل على الكل (ابعته لأصحابك)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // مش محتاجين معالجات x86 (محاكيات فقط) - بيوفر حجم
            excludes += listOf("**/x86/**", "**/x86_64/**")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    val ytdl = "0.17.2"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")
}
