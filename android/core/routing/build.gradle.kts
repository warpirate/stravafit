// SPDX-License-Identifier: AGPL-3.0-or-later
// Route suggestions near the athlete: loop candidates routed by the FOSSGIS OSRM servers, elevation from
// Open-Meteo, scored and cached on the device. No own server; both flavours use this module unchanged.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.openactivity.core.routing"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

dependencies {
    api(project(":shared:model"))
    implementation(project(":shared:geo"))
    implementation(project(":shared:polyline"))
    implementation(project(":shared:metrics"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

// The live smoke test calls the real FOSSGIS OSRM and Open-Meteo servers. It is skipped unless asked for:
// ./gradlew :android:core:routing:testDebugUnitTest -Plive=true --tests '*LiveSmokeTest*'
tasks.withType<Test>().configureEach {
    systemProperty("routing.live", (project.findProperty("live") ?: "false").toString())
    testLogging { showStandardStreams = project.hasProperty("live") }
}
