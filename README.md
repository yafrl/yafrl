# yafrl

![](https://img.shields.io/badge/version-0.1-purple)
![](https://img.shields.io/badge/kotlin-2.1.10-blue)
[![codecov](https://codecov.io/github/Sintrastes/yafrl/graph/badge.svg?token=2A1XMWGOSH)](https://codecov.io/github/Sintrastes/yafrl)
[![Build](https://github.com/Sintrastes/yafrl/actions/workflows/gradle.yml/badge.svg)](https://github.com/Sintrastes/yafrl/actions/workflows/gradle.yml)
[![](https://img.shields.io/badge/Documentation-2403fc)](https://sintrastes.github.io/yafrl/)

**Y**et **A**nother **F**unctional **R**eactive **L**ibrary (**yafrl**) is a small library for _functional reactive programming_ in Kotlin -- meant to provide an alternative
 to constructs from `kotlinx-coroutines` such as `Flow` and `StateFlow`.

For more information, including potential use-cases and some concrete advantages over `Flow`s, see [the docs](https://sintrastes.github.io/yafrl/).

# Quickstart

For non-multiplatform Android / JVM projects:

```
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

```
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
