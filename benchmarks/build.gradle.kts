
kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.coroutines)
                implementation(project(":yafrl-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.14")
            }
        }
    }
}

benchmark {
    configurations {
        all {
            outputTimeUnit = "ms"
            mode = "AverageTime"
        }
    }

    targets {
        register("jvm")
    }
}

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    id("org.jetbrains.kotlinx.benchmark") version "0.4.14"
    kotlin("plugin.allopen") version "2.1.20"
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}