import java.time.Instant

plugins {
  id("com.diffplug.spotless") version "7.0.2"
  id("org.jetbrains.dokka") version "1.9.20"
  application

  kotlin("jvm") version "2.1.10"
  kotlin("plugin.serialization") version "2.1.10"
}

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  api("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")

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

  val log4j = "2.24.3"
  implementation("org.apache.logging.log4j:log4j:$log4j")
  implementation("org.apache.logging.log4j:log4j-core:$log4j")
  implementation("org.apache.logging.log4j:log4j-api:$log4j")
  implementation("org.slf4j:slf4j-nop:2.0.16")

  implementation("commons-configuration:commons-configuration:1.10")
  implementation("commons-pool:commons-pool:1.6")

  val ktorVersion = "3.0.3"
  implementation("io.ktor:ktor-network-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")

  // https://mvnrepository.com/artifact/com.github.chrisvest/stormpot
  implementation("com.github.chrisvest:stormpot:3.2")

  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

  // https://mvnrepository.com/artifact/io.netty/netty-all
  testImplementation("io.netty:netty-all:4.2.0.RC2")

  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.4.4")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

group = "org.emulinker"

description = "EmuLinker-K"

version = "0.15.0"

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

    resources { srcDirs("conf", "src/main/i18n") }
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
    "org.emulinker.testing.TestLoggingBackendFactory#getInstance",
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
