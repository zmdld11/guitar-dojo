plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.zmdld11.guitardojo.core.phrase"
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

// 纯 Kotlin 领域模型：不依赖 Compose / Android API，方便单测与离线工具对齐 schema。
dependencies {
    // @Serializable 出现在公开模型上，用 api 让调用方无需重复声明
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
