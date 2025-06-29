# yafrl

<p align="center">
  <img src="https://yafrol.github.io/yafrl_logo.png" height="256"/>
</p>

<p align="center">
<img src="https://img.shields.io/badge/version-0.3-purple)"/>
<img src="https://img.shields.io/badge/kotlin-2.1.10-blue"/>
<a href="https://codecov.io/github/yafrl/yafrl">
  <img src="https://codecov.io/github/yafrl/yafrl/graph/badge.svg?token=2A1XMWGOSH"/>
</a>
<a href="https://github.com/yafrl/yafrl/actions/workflows/gradle.yml">
  <img src="https://github.com/Sintrastes/yafrl/actions/workflows/gradle.yml/badge.svg"/>
</a>
<a href="https://yafrl.github.io/yafrl/docs/yafrl-core">
 <img src="https://img.shields.io/badge/Documentation-2403fc"/>
</a>
</p>

**Y**et **A**nother **F**unctional **R**eactive **L**ibrary (**yafrl**) is a small library for _functional reactive programming_ in Kotlin -- meant to provide an alternative
 to constructs from `kotlinx-coroutines` such as `Flow` and `StateFlow`.

For more information, including potential use-cases and some concrete advantages over `Flow`s, see [the docs](https://yafrl.github.io/).

# Quickstart

For non-multiplatform Android / JVM projects:

```groovy
dependencies {
    // Basic functionality of the library
    implementation("io.github.yafrl:yafrl-core-jvm:0.3")

    // Optional: kotlinx.coroutines.flow integrations
    implementation("io.github.yafrl:yafrl-core-jvm:0.3")

    // Optional: Utilities for testing yafrl programs
    implementation("io.github.yafrl:yafrl-testing-jvm:0.3")

    // Optional: Jetpack Compose integrations
    implementation("io.github.yafrl:yafrl-compose-jvm:0.3")

    // Optional: Integrations for arrow-optics
    implementation("io.github.yafrl:yafrl-optics-jvm:0.3")
}
```

For KMP projects (Kotlin DSL): 

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Basic functionality of the library
                implementation("io.github.yafrl:yafrl-core:0.3")

                // Optional: kotlinx.coroutines.flow integrations
                implementation("io.github.yafrl:yafrl-core:0.3")

                // Optional: Utilities for testing yafrl programs
                implementation("io.github.yafrl:yafrl-testing:0.3")

                // Optional: Jetpack Compose integrations
                implementation("io.github.yafrl:yafrl-compose:0.3")

                // Optional: Integrations for arrow-optics
                implementation("io.github.yafrl:yafrl-optics:0.3")
            }
        }
    }
}
```
