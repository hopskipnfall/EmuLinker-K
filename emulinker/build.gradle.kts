import java.time.Instant
import org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs

plugins {
  id("com.diffplug.spotless") version "6.18.0"
  id("org.jetbrains.dokka") version "1.8.10"
  application

  // Serialization.
  kotlin("jvm") version "1.8.21"
  kotlin("plugin.serialization") version "1.8.21"

  kotlin("kapt") version "1.8.21"
}

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  api("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

  implementation("io.github.redouane59.twitter:twittered:2.22")

  api("io.dropwizard.metrics:metrics-core:4.2.3")
  api("io.dropwizard.metrics:metrics-jvm:4.2.3")

  api("com.google.flogger:flogger:0.7.4")
  api("com.google.flogger:flogger-system-backend:0.7.4")
  api("com.google.flogger:flogger-log4j2-backend:0.7.4")

  implementation("com.google.dagger:dagger:2.45")
  kapt("com.google.dagger:dagger-compiler:2.45")

  api("org.apache.logging.log4j:log4j:2.20.0")
  api("org.apache.logging.log4j:log4j-core:2.20.0")
  api("org.apache.logging.log4j:log4j-api:2.20.0")

  api("org.mortbay.jetty:jetty:4.2.12")
  api("commons-configuration:commons-configuration:1.1")
  api("commons-pool:commons-pool:1.2")

  val ktorVersion = "2.3.0"
  api("io.ktor:ktor-network-jvm:$ktorVersion")
  api("io.ktor:ktor-server-core-jvm:$ktorVersion")
  api("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  api("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
  api("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
  api("io.ktor:ktor-client-core:$ktorVersion")
  api("io.ktor:ktor-client-cio:$ktorVersion")

  api("io.reactivex.rxjava3:rxjava:3.1.1")

  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
  testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
  testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

group = "org.emulinker"

description = "EmuLinker-K"

version = "0.11.2"

kotlin { jvmToolchain(17) }

// Copy/filter files before compiling.
tasks.processResources {
  from("src/main/java-templates") {
    include("**/*")

    expand(
      mapOf(
        "isDev" to properties["prodBuild"].toString().toBoolean().not(),
        "buildTimestampSeconds" to Instant.now().epochSecond,
        "project" to
          object {
            val name = project.description
            val version = project.version
            val url = properties["url"]
          },
      )
    )
  }
}

sourceSets {
  main {
    kotlin.srcDir("src/main/java")
    kotlin.srcDir("build/resources/main")

    resources { srcDirs("conf") }
  }

  test {
    kotlin.srcDir("src/test/java")
    kotlin.srcDir("build/resources/test")

    resources { srcDirs("conf") }
  }
}

// Dagger.
tasks.withType<KaptGenerateStubs> {
  // Filtering the resources has to happen first.
  dependsOn(":emulinker:processResources")
}

tasks.named("compileTestKotlin") { dependsOn(":emulinker:processTestResources") }

tasks.withType<Test> {
  useJUnitPlatform()
  useJUnit()

  systemProperty(
    "flogger.backend_factory",
    "org.emulinker.testing.TestLoggingBackendFactory#getInstance"
  )
}

// Formatting/linting.
spotless {
  kotlin {
    target("**/*.kt", "**/*.kts")
    targetExclude("build/", ".git/", ".idea/", ".mvn", "src/main/java-templates/")
    ktfmt().googleStyle()
  }

  yaml {
    target("**/*.yml", "**/*.yaml")
    targetExclude("build/", ".git/", ".idea/", ".mvn")
    jackson()
  }
}

application { mainClass.set("org.emulinker.kaillera.pico.ServerMainKt") }

// "jar" task makes a single jar including all dependencies.
tasks.jar {
  manifest { attributes["Main-Class"] = application.mainClass }

  from(configurations.runtimeClasspath.get().map { zipTree(it) })
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  archiveBaseName.set("emulinker-k")
}

// kdoc generation support.
subprojects { apply(plugin = "org.jetbrains.dokka") }
