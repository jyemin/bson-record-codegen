plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(path = ":lib", configuration = "default"))
    // This dependency is exported to consumers, that is to say found on their compile classpath.
    implementation(libs.bson.record.codec)
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator.annprocess)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run JMH benchmarks."
    mainClass = "org.openjdk.jmh.Main"
    classpath = sourceSets.main.get().runtimeClasspath
}

