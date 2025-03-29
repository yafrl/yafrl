pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "yafrl"
include("core")
project(":core").name = "yafrl-core"

include("coroutines")
project(":coroutines").name = "yafrl-coroutines"

include("compose")
project(":compose").name = "yafrl-compose"

include("testing")
project(":testing").name = "yafrl-testing"

include("optics")
project(":optics").name = "yafrl-optics"
