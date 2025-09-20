import java.net.URL
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.gradle.DokkaBasePlugin
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.dokka.gradle.tasks.DokkaBaseTask

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:2.0.0")
    }
}

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

allprojects {
    version = "0.4-SNAPSHOT"
    group = "io.github.yafrl"

    repositories {
        mavenCentral()
        google()
    }
}

kotlin {
    jvm()
}

tasks.withType<DokkaMultiModuleTask>().configureEach {
    pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
        customAssets = listOf(File(rootDir, "dokka/yafrl_logo.png"))
        customStyleSheets = listOf(
            File(rootDir, "dokka/logo-styles.css"),
            File(rootDir, "dokka/dokka-style.css")
        )
        templatesDir = File(rootDir, "dokka/templates")
    }
}

subprojects {
    // Setup dokka config in each subproject.
    plugins.withId("org.jetbrains.dokka") {
        tasks.withType<DokkaTaskPartial>().configureEach {
            pluginConfiguration<DokkaBase, DokkaBaseConfiguration> {
                customAssets = listOf(File(rootDir, "dokka/yafrl_logo.png"))
                customStyleSheets = listOf(
                    File(rootDir, "dokka/logo-styles.css"),
                    File(rootDir, "dokka/dokka-style.css")
                )
                templatesDir = File(rootDir, "dokka/templates")
            }

            dokkaSourceSets {
                dokkaSourceSets.matching { it.name == "commonMain" }.configureEach {
                    val projectName = project.name
                    moduleName.set(projectName)
                    includes.from("Module.md")

                    sourceLink {
                        localDirectory.set(file("src/commonMain/kotlin"))
                        remoteUrl.set(URL("https://github.com/yafrl/yafrl/tree/main/$projectName/src/commonMain/kotlin"))
                        remoteLineSuffix.set("#L")
                    }
                }
            }
        }
    }
}

subprojects {
    if (this == rootProject) return@subprojects

    // Setup publish config in each subproject
    plugins.withId("com.vanniktech.maven.publish") {
        mavenPublishing {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()

            coordinates(group.toString(), project.name, version.toString())

            pom {
                name.set(project.name)
                description.set(
                    project.findProperty("projectDescription") as String
                )
                inceptionYear.set("2025")
                url.set("https://github.com/yafrl/yafrl/")

                licenses {
                    license {
                        name.set("The MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("https://opensource.org/license/mit")
                    }
                }

                developers {
                    developer {
                        id.set("sintrastes")
                        name.set("Nathan Bedell")
                        url.set("https://github.com/sintrastes/")
                    }
                }

                scm {
                    url.set("https://github.com/yafrl/yafrl/")
                    connection.set("scm:git:git://github.com/yafrl/yafrl.git")
                    developerConnection.set("scm:git:ssh://git@github.com/yafrl/yafrl.git")
                }
            }
        }
    }
}
