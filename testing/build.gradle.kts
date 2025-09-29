
extra["projectDescription"] =
    "Yet Another Functional Reactive Library - kotlinx.coroutines integration"

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.maven)
    alias(libs.plugins.kover)
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotest)
}

repositories {
    mavenCentral()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvm()
    js(IR).browser() {
        testTask {
            // Currently failing due to kotest issue resolving Arb for Unit
            enabled = false
        }
    }

    // Native: https://kotlinlang.org/docs/native-target-support.html
    // -- Tier 1 --
    linuxX64()
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    // -- Tier 2 --
    linuxArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()
    iosArm64()
    // -- Tier 3 --
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(kotlin("test"))
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotest.property)
                implementation(libs.kotest.assertions)
                implementation(libs.serialization)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.runner)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotest.engine)
            }
        }
    }
}
