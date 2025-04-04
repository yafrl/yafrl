
extra["projectDescription"] =
    "Yet Another Functional Reactive Library - kotlinx.coroutines integration"

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.maven)
    alias(libs.plugins.kover)
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
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
    }
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
                implementation(libs.kotlin.coroutines)
                implementation(compose.runtime)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.molecule)
                implementation(libs.kotest.runner)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotest.engine)
                implementation(libs.kotlin.datetime)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.coroutines.test)
            }
        }
    }
}
