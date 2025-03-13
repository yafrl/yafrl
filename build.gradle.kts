import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    kotlin("multiplatform") version "2.1.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("org.jetbrains.kotlinx.atomicfu") version "0.27.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.dokka") version "2.0.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("com.vanniktech.maven.publish") version "0.31.0"
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm()
    iosArm64()
    macosX64()
    js().browser()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("app.cash.molecule:molecule-runtime:2.0.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("commonMain") {
            // used as project name in the header
            moduleName.set("yafrl")

            // contains descriptions for the module and the packages
            includes.from("Module.md")

            // adds source links that lead to this repository, allowing readers
            // to easily find source code for inspected declarations
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(URL("https://github.com/sintrastes/yafrl/tree/main/src/commonMain/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

dependencies {
    commonMainImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    commonMainImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

kotlin {
    jvmToolchain(17)
}

group = "io.github.sintrastes"
version = "0.1"

mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01)

    signAllPublications()

    coordinates(group.toString(), "yafrl", version.toString())

    pom {
        name = "yafrl"
        description = "Yet Another Functional Reactive Library"
        inceptionYear = "2025"
        url = "https://github.com/sintrastes/yafrl/"
        licenses {
            license {
                name = "The MIT License"
                url = "https://opensource.org/license/mit"
                distribution = "https://opensource.org/license/mit"
            }
        }
        developers {
            developer {
                id = "sintrastes"
                name = "Nathan Bedell"
                url = "https://github.com/sintrastes/"
            }
        }
        scm {
            url = "https://github.com/sintrastes/yafrl/"
            connection = "scm:git:git://github.com/sintrastes/yafrl.git"
            developerConnection = "scm:git:ssh://git@github.com/sintrastes/yafrl.git"
        }
    }
}
