@file:OptIn(ExperimentalComposeLibrary::class)

import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ExperimentalComposeLibrary
import yairm210.purity.PurityConfiguration

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
    alias(libs.plugins.purity)
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    iosArm64()
    macosX64()
    macosArm64()
    js().browser()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":yafrl-core"))
                implementation(libs.kotlin.coroutines)
                implementation(compose.material)
                // TODO: Probably want to split the stuff that depends on this into another module.
                implementation(libs.arrow.optics)
                 implementation(libs.purity.annotations)
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

configure<PurityConfiguration> {
    warnOnPossibleAnnotations = true

    wellKnownPureFunctions = setOf(
        "kotlin.collections.isNotEmpty",
        "kotlin.collections.sumOf"
    )
    wellKnownPureClasses = setOf()
    wellKnownReadonlyClasses = setOf()
    wellKnownReadonlyFunctions = setOf()
    wellKnownInternalStateClasses = setOf()
}