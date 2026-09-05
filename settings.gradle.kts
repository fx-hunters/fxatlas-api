plugins {
    // Java toolchain(17)을 로컬에 없을 경우 자동 프로비저닝
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "fxatlas-api"

include("engine", "app")
