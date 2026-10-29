// SPDX-License-Identifier: AGPL-3.0-or-later
// Share/export plumbing (ADR-002): route-art rendering to PNG/PDF, GPX/TCX/FIT/GeoJSON/SVG writers,
// FileProvider, share intents and MediaStore saving. Input is the neutral ExportActivity model, so this
// module never depends on the database.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.openactivity.core.export"
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
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":shared:model"))
    implementation(project(":shared:geo"))
    api(project(":lib:route-art"))
    implementation(project(":lib:gpx-tcx"))
    implementation(project(":lib:fit"))
    implementation(project(":lib:geojson"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidsvg)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":lib:gpx-tcx"))
    testImplementation(project(":lib:fit"))
    testImplementation(project(":lib:geojson"))
}
