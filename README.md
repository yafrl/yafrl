# yafrl

<p align="center">
  <img src="https://avatars.githubusercontent.com/u/205696199?s=216&v=4"/>
</p>

<p align="center">
<img src="https://img.shields.io/badge/version-0.1-purple)"/>
<img src="https://img.shields.io/badge/kotlin-2.1.10-blue"/>
<a href="https://codecov.io/github/Sintrastes/yafrl">
  <img src="https://codecov.io/github/Sintrastes/yafrl/graph/badge.svg?token=2A1XMWGOSH"/>
</a>
<a href="https://github.com/Sintrastes/yafrl/actions/workflows/gradle.yml">
  <img src="https://github.com/Sintrastes/yafrl/actions/workflows/gradle.yml/badge.svg"/>
</a>
<a href="https://sintrastes.github.io/yafrl/">
 <img src="https://img.shields.io/badge/Documentation-2403fc"/>
</a>
</p>

**Y**et **A**nother **F**unctional **R**eactive **L**ibrary (**yafrl**) is a small library for _functional reactive programming_ in Kotlin -- meant to provide an alternative
 to constructs from `kotlinx-coroutines` such as `Flow` and `StateFlow`.

For more information, including potential use-cases and some concrete advantages over `Flow`s, see [the docs](https://sintrastes.github.io/yafrl/).

# Quickstart

For non-multiplatform Android / JVM projects:

```groovy
dependencies {
    // Basic functionality of the library
    implementation("io.github.sintrastes:yafrl-core-jvm:0.2")

    // Optional: kotlinx.coroutines.flow integrations
    implementation("io.github.sintrastes:yafrl-core-jvm:0.2")

    // Optional: Utilities for testing yafrl programs
    implementation("io.github.sintrastes:yafrl-testing-jvm:0.2")

    // Optional: Jetpack Compose integrations
    implementation("io.github.sintrastes:yafrl-compose-jvm:0.2")

    // Optional: Integrations for arrow-optics
    implementation("io.github.sintrastes:yafrl-optics-jvm:0.2")
}
```

For KMP projects (Kotlin DSL): 

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Basic functionality of the library
                implementation("io.github.sintrastes:yafrl-core:0.2")

                // Optional: kotlinx.coroutines.flow integrations
                implementation("io.github.sintrastes:yafrl-core:0.2")

                // Optional: Utilities for testing yafrl programs
                implementation("io.github.sintrastes:yafrl-testing:0.2")

                // Optional: Jetpack Compose integrations
                implementation("io.github.sintrastes:yafrl-compose:0.2")

                // Optional: Integrations for arrow-optics
                implementation("io.github.sintrastes:yafrl-optics:0.2")
            }
        }
    }
}
```
