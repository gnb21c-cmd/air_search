pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
    }
}
dependencyResolutionManagement {
    repositories {
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
    }
}
rootProject.name = "air-search"
include(":core")
// 안드로이드 SDK 가 없는 곳(규칙 시험만 할 때)은 -PcoreOnly 로 core 만 묶는다
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
