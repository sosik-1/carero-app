import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "app.carero"
    compileSdk = 35
    // AAOS CarPropertyManager(차속) 사용
    useLibrary("android.car")

    defaultConfig {
        applicationId = "app.carero"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.4"
    }

    signingConfigs {
        // 공모전 심사·시연용 release 빌드 서명.
        // 저장소에 키를 올리지 않기 위해 로컬 debug 키로 서명합니다.
        // (앱 마켓 입점 시에는 별도 배포 키로 교체)
        getByName("debug")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi.helper)

    testImplementation(libs.junit)
}
