import java.net.URL
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("multiplatform") version "2.1.20"
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

allprojects {
    version = "0.3"
    group = "io.github.yafrl"

    repositories {
        mavenCentral()
        google()
    }
}

kotlin {
    jvm()
}

subprojects {
    if (this == rootProject) return@subprojects

    // Setup dokka config in each subproject.
    plugins.withId("org.jetbrains.dokka") {
        tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
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
