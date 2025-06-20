@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.ksp)
}

repositories {
    mavenCentral()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvm()
    iosArm64()
    macosX64()
    js().browser()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(project(":yafrl-compose"))
                implementation(libs.kotlin.coroutines)
                implementation(compose.material)
                implementation(compose.desktop.currentOs)
                implementation(compose.material)
                implementation(libs.molecule)
                implementation(libs.arrow.optics)
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.molecule)
                implementation(libs.kotest.engine)
                implementation(libs.kotest.runner)
                implementation(libs.kotest.assertions)
                implementation(compose.uiTest)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.coroutines.test)
                implementation(libs.mockk)
            }
        }
    }
}

dependencies {
    ksp("io.arrow-kt:arrow-optics-ksp-plugin:2.1.0")
}