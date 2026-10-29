// SPDX-License-Identifier: AGPL-3.0-or-later
// The recording foreground service: feeds RecordingEngine from location and barometer, journals to Room
// for crash recovery, shows the ongoing notification, speaks audio cues and saves finished activities.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.openactivity.core.recordingservice"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("gms") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        (it as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = false
    }
}

val gpsCorpusDir = rootProject.layout.projectDirectory.dir("testdata/gps-corpus")

tasks.withType<Test>().configureEach {
    systemProperty("gpsCorpusDir", gpsCorpusDir.asFile.absolutePath)
    inputs.dir(gpsCorpusDir).withPropertyName("gpsCorpus").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    api(project(":android:core:recording"))
    api(project(":android:core:location"))
    api(project(":android:core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Permission helper UI (rationale sheet, settings deep link).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
