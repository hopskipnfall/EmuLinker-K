import org.gradle.kotlin.dsl.maven

plugins {
    // Define versions for plugins used in subprojects
    kotlin("jvm") version "2.1.0" apply false
    kotlin("multiplatform") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    id("com.google.protobuf") version "0.9.5" apply false
    id("build.buf") version "0.10.2" apply false
    id("com.diffplug.spotless") version "7.2.1" apply false
    id("org.jetbrains.dokka") version "2.0.0" apply false
    id("me.champeau.jmh") version "0.7.2" apply false
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
