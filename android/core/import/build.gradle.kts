// SPDX-License-Identifier: AGPL-3.0-or-later
// File import plumbing (ADR-002): parses user-picked GPX/TCX/FIT files (optionally gzipped) into the
// neutral ImportedActivity model with a de-duplication key. Storage is up to the caller.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.openactivity.core.fileimport"
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
            all {
                it.systemProperty("testdata.dir", rootProject.layout.projectDirectory.dir("testdata/files").asFile.absolutePath)
            }
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
    implementation(project(":lib:gpx-tcx"))
    implementation(project(":lib:fit"))

    implementation(libs.androidx.core.ktx)
    // api: ImportDocumentsContract is public and extends ActivityResultContracts.OpenMultipleDocuments.
    api(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":lib:fit"))
}
