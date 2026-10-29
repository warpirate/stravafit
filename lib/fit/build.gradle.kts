// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val testDataDir = rootProject.layout.projectDirectory.dir("testdata/files")

tasks.test {
    useJUnitPlatform()
    inputs.dir(testDataDir).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("testdata.dir", testDataDir.asFile.absolutePath)
    // UPDATE_GOLDENS=true ./gradlew :lib:fit:test rewrites golden outputs instead of comparing.
    systemProperty("goldens.update", System.getenv("UPDATE_GOLDENS") ?: "false")
}
