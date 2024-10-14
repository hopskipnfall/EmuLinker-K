import java.time.Instant

plugins {
  id("com.diffplug.spotless") version "6.25.0"
  id("org.jetbrains.dokka") version "1.9.20"
  application

  kotlin("jvm") version "2.0.20"
  kotlin("plugin.serialization") version "2.0.20"

  id("com.google.devtools.ksp") version "2.0.20-1.0.24"
}

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  api("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

  implementation("io.github.redouane59.twitter:twittered:2.23")

  implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.0.0"))
  implementation("io.insert-koin:koin-core")
  testImplementation("io.insert-koin:koin-test")
  testImplementation("io.insert-koin:koin-test-junit4")
  //  testImplementation("io.insert-koin:koin-test-junit5")

  api("io.dropwizard.metrics:metrics-core:4.2.3")
  api("io.dropwizard.metrics:metrics-jvm:4.2.3")

  val floggerVersion = "0.8"
  api("com.google.flogger:flogger:$floggerVersion")
  api("com.google.flogger:flogger-system-backend:$floggerVersion")
  api("com.google.flogger:flogger-log4j2-backend:$floggerVersion")

  val daggerVersion = "2.52"
  implementation("com.google.dagger:dagger:$daggerVersion")
  implementation("com.google.dagger:dagger-compiler:$daggerVersion")
  ksp("com.google.dagger:dagger-compiler:$daggerVersion")

  val log4j = "2.23.1"
  api("org.apache.logging.log4j:log4j:$log4j")
  api("org.apache.logging.log4j:log4j-core:$log4j")
  api("org.apache.logging.log4j:log4j-api:$log4j")

  api("org.mortbay.jetty:jetty:4.2.12")
  api("commons-configuration:commons-configuration:1.1")
  api("commons-pool:commons-pool:1.2")

  val ktorVersion = "2.3.12"
  implementation("io.ktor:ktor-network-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")

  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

  // This is only used by the fake testing client, hopefully we can remove this.
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.4.4")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

group = "org.emulinker"

description = "EmuLinker-K"

version = "0.13.2"

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
            val prerelease = properties["prerelease"]
          },
        "useBytereadpacketInsteadOfBytebuffer" to false,
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

tasks.named("compileKotlin") {
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

tasks.withType<JavaExec> { jvmArgs = listOf("-Xms512m", "-Xmx512m") }
