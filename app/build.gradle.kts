import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub Actions 실행 번호를 버전으로 (새 설치파일이 늘 더 높은 번호)
val buildNo = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "kr.flightalert"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.flightalert"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNo
        versionName = "1.0.$buildNo"
    }

    // 늘 같은 키로 서명해야 새 버전을 덮어 설치할 수 있다 (개인용 앱이라 키를 저장소에 둠)
    signingConfigs {
        create("fixed") {
            storeFile = file("flightalert.keystore")
            storePassword = "flightalert"
            keyAlias = "flightalert"
            keyPassword = "flightalert"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
