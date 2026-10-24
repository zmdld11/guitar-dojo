plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.zmdld11.guitardojo.core.audio"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// 音频核心对外保持纯 Kotlin：不依赖 Compose、Activity、Context。
dependencies {
    // Phrase 出现在 PracticeEngine 的公开 API 上
    api(project(":core:phrase"))
    // SharedFlow 出现在公开 API 上，用 api 让调用方无需重复声明
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
