extra["projectDescription"] =
    "Yet Another Functional Reactive Library - Arrow optics integrations."

plugins {
    kotlin("multiplatform") version "2.1.10"
    id("com.google.devtools.ksp") version "2.1.10-1.0.30"
    id("org.jetbrains.kotlinx.atomicfu") version "0.27.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
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
                implementation("io.arrow-kt:arrow-optics:2.0.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

dependencies {
    ksp("io.arrow-kt:arrow-optics-ksp-plugin:2.0.1")
}