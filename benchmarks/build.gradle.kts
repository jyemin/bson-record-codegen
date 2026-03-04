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
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(24)
    }
    args = listOf("-f", "1", "-wi", "3", "-i", "5")
}

tasks.register<JavaExec>("jmhProfile") {
    group = "benchmark"
    description = "Run JMH benchmarks with stack profiler."
    mainClass = "org.openjdk.jmh.Main"
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(24)
    }
    args = listOf("-f", "1", "-wi", "2", "-i", "3", "-prof", "stack:lines=10;detailLine=true", "decode")
}

tasks.register<JavaExec>("jmhLarge") {
    group = "benchmark"
    description = "Run JMH benchmarks for LargeRecord."
    mainClass = "org.openjdk.jmh.Main"
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(24)
    }
    args = listOf("-f", "1", "-wi", "3", "-i", "5", "LargeRecord")
}

tasks.register<JavaExec>("jmhLargeProfile") {
    group = "benchmark"
    description = "Run JMH benchmarks for LargeRecord with profiler."
    mainClass = "org.openjdk.jmh.Main"
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(24)
    }
    args = listOf("-f", "1", "-wi", "2", "-i", "3", "-prof", "stack:lines=10;detailLine=true", "LargeRecord.*decode")
}

