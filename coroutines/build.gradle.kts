
extra["projectDescription"] =
    "Yet Another Functional Reactive Library - kotlinx.coroutines integration"

plugins {
    kotlin("multiplatform") version "2.1.10"
    id("org.jetbrains.kotlinx.atomicfu") version "0.27.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("org.jetbrains.dokka") version "2.0.0"
    id("io.kotest.multiplatform") version "6.0.0.M2"
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
    js(IR).browser() {
        testTask {
            enabled = false
        }
    }

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
                implementation("io.kotest:kotest-runner-junit5:6.0.0.M2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation("io.kotest:kotest-framework-engine:6.0.0.M2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
    }
}
