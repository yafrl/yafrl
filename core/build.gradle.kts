import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import yairm210.purity.PurityConfiguration

extra["projectDescription"] =
    "Yet Another Functional Reactive Library - core library"

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven)
    alias(libs.plugins.serialization)
    alias(libs.plugins.purity)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    js(IR) {
        browser {
            testTask {
                // useMocha()

                // Currently failing `Tick emits events at specified intervals` for some reason.
                enabled = false
            }
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
                implementation(libs.kotlin.collections.immutable)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.coroutines.test)
                implementation(libs.kotlin.datetime)
                implementation(libs.serialization)
                implementation(libs.kotlinx.io)
                implementation(libs.purity.annotations)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotest.assertions)
                implementation(libs.kotest.engine)
                implementation(libs.kotest.property)
                implementation(libs.kotlin.datetime)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.coroutines.test)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner)
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

configure<PurityConfiguration> {
    warnOnPossibleAnnotations = true

    wellKnownPureFunctions = setOf(
        // These are not actually pure, but we treat them as pure
        // to allow for the correct constraints on combinators for external users of the library.
        "io.github.yafrl.timeline.Timeline.cachedSampleValue",
        "io.github.yafrl.behaviors.Behavior.sampleValueAt",
        "io.github.yafrl.timeline.Timeline.fetchNodeValue",
        "io.github.yafrl.timeline.current",
        "kotlinx.atomicfu.locks.synchronized",
        "io.github.yafrl.timeline.Node.<get-dirty>",
        "io.github.yafrl.timeline.Node.<set-dirty>",
        "io.github.yafrl.timeline.Node.<get-rawValue>",
        "io.github.yafrl.timeline.Node.<set-rawValue>",
        // Actually pure
        "kotlin.collections.plus",
        "kotlin.collections.drop"
    )
    wellKnownPureClasses = setOf()
    wellKnownReadonlyClasses = setOf()
    wellKnownReadonlyFunctions = setOf()
    wellKnownInternalStateClasses = setOf()
}
