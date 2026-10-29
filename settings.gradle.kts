pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "stravafit"

// Include only modules that exist, so partial checkouts and new modules don't break configuration.
fun includeIfPresent(vararg paths: String) {
    for (p in paths) {
        val dir = file(p.removePrefix(":").replace(':', '/'))
        if (dir.resolve("build.gradle.kts").exists()) include(p)
    }
}

// Shared pure-Kotlin code (KMP-ready; JVM target for now)
includeIfPresent(":shared:model", ":shared:geo", ":shared:polyline", ":shared:metrics")

// Reusable Apache-2.0 libraries
includeIfPresent(":lib:route-art", ":lib:gpx-tcx", ":lib:geojson", ":lib:fit")

// Android
includeIfPresent(
    ":android:core:recording",
    ":android:core:designsystem",
    ":android:app",
    ":android:core:database", // WP data-service
    ":android:core:datastore", // WP data-service
    ":android:core:location", // WP data-service
    ":android:core:data", // WP data-service
    ":android:core:recording-service", // WP data-service
    ":android:core:map", // WP maps
    ":android:core:export", // WP export
    ":android:core:import", // WP export
    ":android:feature:share-export", // WP export
    ":android:feature:record", // WP record
)
