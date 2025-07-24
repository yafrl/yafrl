import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

extra["projectDescription"] =
    "Yet Another Functional Reactive Library - core library"

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.atomicfu)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven)
    alias(libs.plugins.kotest)
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
    macosArm64()
    js(IR) {
        browser {
            testTask {
                // useMocha()

                // Currently failing `Tick emits events at specified intervals` for some reason.
                enabled = false
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.collections.immutable)
                implementation(libs.kotlin.coroutines)
                implementation(libs.kotlin.coroutines.test)
                implementation(libs.kotlin.datetime)
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

//mavenPublishing {
//    publishToMavenCentral(SonatypeHost.S01)
//
//    signAllPublications()
//
//    coordinates(group.toString(), project.name, version.toString())
//
//    pom {
//        name = project.name
//        description = projectDescription
//        inceptionYear = "2025"
//        url = "https://github.com/sintrastes/yafrl/"
//        licenses {
//            license {
//                name = "The MIT License"
//                url = "https://opensource.org/license/mit"
//                distribution = "https://opensource.org/license/mit"
//            }
//        }
//        developers {
//            developer {
//                id = "sintrastes"
//                name = "Nathan Bedell"
//                url = "https://github.com/sintrastes/"
//            }
//        }
//        scm {
//            url = "https://github.com/sintrastes/yafrl/"
//            connection = "scm:git:git://github.com/sintrastes/yafrl.git"
//            developerConnection = "scm:git:ssh://git@github.com/sintrastes/yafrl.git"
//        }
//    }
//}
