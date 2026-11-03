// SPDX-License-Identifier: AGPL-3.0-or-later
// Home-screen widget (Jetpack Glance): start, control and share a recording without opening the app.
// Spec: docs/08-blueprint/widgets-spec.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.openactivity.feature.widgets"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // Same as :feature:record: the app picks the gms/foss flavour of the recording service.
        missingDimensionStrategy("distribution", "foss")
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
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":android:core:recording-service"))
    implementation(project(":android:core:data"))
    implementation(project(":android:core:export")) // route drawing on "Last activity" (route-art + SVG rasterizer)
    implementation(project(":shared:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.glance.appwidget.testing)
}
