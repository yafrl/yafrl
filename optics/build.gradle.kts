extra["projectDescription"] =
    "Yet Another Functional Reactive Library - Arrow optics integrations."

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.ksp)
    alias(libs.plugins.atomicfu)
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
                implementation(libs.arrow.optics)
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