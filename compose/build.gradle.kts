@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary

extra["projectDescription"] =
    "Yet Another Functional Reactive Library - Jetpack Compose integration."

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.maven)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
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
                implementation(libs.kotlin.coroutines)
                implementation(compose.material)
                // TODO: Probably want to split the stuff that depends on this into another module.
                implementation(libs.arrow.optics)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.uiTest)
                implementation(compose.material)
                implementation(compose.desktop.uiTestJUnit4)
                implementation(libs.mockk)
                implementation(libs.molecule)

                implementation(kotlin("test"))
            }
        }
    }
}