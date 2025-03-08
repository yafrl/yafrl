import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.test

plugins {
    kotlin("multiplatform") version "2.1.10"
}

kotlin {
    jvm() {
//        test {
//            useJUnitPlatform()
//        }
    }
    iosArm64()
    macosX64()
    js().browser()

    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

group = "io.github.sintrastes"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    commonMainImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    commonMainImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

kotlin {
    jvmToolchain(8)
}