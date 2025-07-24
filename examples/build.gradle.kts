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
    macosArm64() {
        binaries {
            executable {
                entryPoint = "ui.playGame"
            }
        }
    }
    js().browser()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(project(":yafrl-compose"))
                implementation(libs.arrow.optics)
                implementation(libs.kotest.property)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.coroutines)
                implementation(compose.material)
                implementation(compose.desktop.currentOs)
                implementation(compose.material)
                implementation(libs.molecule)
                implementation(kotlin("test"))
            }
        }

        val macosArm64Main by getting {
            dependencies {
                implementation("com.jakewharton.mosaic:mosaic-runtime:0.17.0")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(project(":yafrl-testing"))
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

// Lets us be able to test the text adventure game from gradle.
afterEvaluate {
    tasks.withType<JavaExec> {
        standardInput = System.`in`
    }
}

dependencies {
    ksp("io.arrow-kt:arrow-optics-ksp-plugin:2.1.0")
}