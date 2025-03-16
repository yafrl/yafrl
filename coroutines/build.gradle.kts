
extra["projectDescription"] =
    "Yet Another Functional Reactive Library - kotlinx.coroutines integration"

plugins {
    kotlin("multiplatform") version "2.1.10"
    id("org.jetbrains.kotlinx.atomicfu") version "0.27.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    iosArm64()
    macosX64()
    js().browser()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation(compose.runtime)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("app.cash.molecule:molecule-runtime:2.0.0")
                implementation(kotlin("test"))
            }
        }
    }
}
