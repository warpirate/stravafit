// SPDX-License-Identifier: AGPL-3.0-or-later
// Health Connect behind a small interface (HealthConnectGateway): availability, permissions, writing
// finished activities as exercise sessions and reading other apps' workouts. The mapping between our
// sports and units and Health Connect's is pure code with unit tests; the app owns the toggles and jobs.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.openactivity.core.healthconnect"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":shared:model"))
    api(project(":android:core:import"))
    // api: the permission contract and the Record types appear in this module's public API.
    api(libs.androidx.health.connect.client)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
