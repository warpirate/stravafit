plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// ./gradlew :lib:route-art:samples  -> writes demo SVGs to lib/route-art/samples/ (synthetic tracks live in the test source set, never in the shipped library)
tasks.register<JavaExec>("samples") {
    group = "application"
    description = "Render sample route-art SVGs"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("routeexport.SamplesKt")
    args(layout.projectDirectory.dir("samples").asFile.absolutePath)
}
