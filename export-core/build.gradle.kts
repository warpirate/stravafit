plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// ./gradlew :export-core:samples  -> writes demo SVGs to export-core/samples/
tasks.register<JavaExec>("samples") {
    group = "application"
    description = "Render sample route-art SVGs"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("routeexport.SamplesKt")
    args(layout.projectDirectory.dir("samples").asFile.absolutePath)
}
