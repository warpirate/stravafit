// SPDX-License-Identifier: AGPL-3.0-or-later
// Pure Kotlin/JVM on purpose: the recording engine has no android.* dependency, so the same code runs in
// the app, in JVM golden-replay tests and on the server.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":shared:model"))
    implementation(project(":shared:geo"))
    api(project(":shared:metrics"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.serialization.json)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val gpsCorpusDir = rootProject.layout.projectDirectory.dir("testdata/gps-corpus")

tasks.test {
    useJUnitPlatform()
    systemProperty("gpsCorpusDir", gpsCorpusDir.asFile.absolutePath)
    inputs.dir(gpsCorpusDir).withPropertyName("gpsCorpus").withPathSensitivity(PathSensitivity.RELATIVE)
}

// ./gradlew :android:core:recording:generateGpsCorpus
// Regenerates testdata/gps-corpus (tracks, goldens, manifest) from the seeded TrackGen scenarios.
// Goldens hold generator ground truth, never engine output.
tasks.register<JavaExec>("generateGpsCorpus") {
    group = "verification"
    description = "Regenerate the synthetic GPS corpus and its golden files from TrackGen"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openactivity.recording.corpus.GenerateCorpusKt")
    args(gpsCorpusDir.asFile.absolutePath)
}
