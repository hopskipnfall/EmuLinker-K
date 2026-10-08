import com.google.protobuf.gradle.id
import java.time.Instant
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  id("com.google.protobuf") version "0.10.0"
  id("build.buf") version "0.11.1"
  id("com.diffplug.spotless") version "8.10.3"
  id("org.jetbrains.dokka") version "2.2.0"
  application

  kotlin("jvm") version "2.4.20"
  kotlin("plugin.serialization") version "2.4.20"
  id("me.champeau.jmh") version "0.7.3"
  id("com.github.ben-manes.versions") version "0.65.0"
}

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  api("org.jetbrains.kotlin:kotlin-stdlib:2.4.20")

  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

  implementation("io.github.redouane59.twitter:twittered:2.23")

  // twittered 2.23 drags in very old transitive dependencies with known CVEs (jackson 2.13.4,
  // snakeyaml 1.31, guava 10.0.1, gson 2.8.9, commons-io 2.4, httpclient 4.5.13, ...). Pin
  // patched versions here. See docs/code_health_report.md.
  implementation(project.dependencies.platform("com.fasterxml.jackson:jackson-bom:2.22.3"))
  constraints {
    implementation("org.yaml:snakeyaml:2.7") { because("CVE-2022-1471 and others in 1.31") }
    implementation("com.google.guava:guava:33.7.2-jre") {
      because("twittered pulls guava 10.0.1 (multiple CVEs)")
    }
    implementation("com.google.code.gson:gson:2.14.0") { because("CVE-2022-25647 in 2.8.9") }
    implementation("commons-io:commons-io:2.22.0") { because("CVE-2021-29425 in 2.4") }
    implementation("commons-codec:commons-codec:1.22.1") { because("old 1.11 from twittered") }
    implementation("org.apache.httpcomponents:httpclient:4.5.14") {
      because("old 4.5.13 from twittered")
    }
    implementation("org.apache.httpcomponents:httpcore:4.4.16") { because("see httpclient") }
  }

  implementation(project.dependencies.platform("io.netty:netty-bom:4.2.19.Final"))
  // Native epoll transport (Linux): batches UDP reads/writes into single system calls. The server
  // falls back to the portable NIO transport anywhere the native library cannot be loaded.
  implementation("io.netty:netty-transport-classes-epoll")
  runtimeOnly("io.netty:netty-transport-native-epoll::linux-x86_64")
  runtimeOnly("io.netty:netty-transport-native-epoll::linux-aarch_64")
  implementation(project.dependencies.platform("io.insert-koin:koin-bom:4.2.2"))
  implementation("io.insert-koin:koin-core")
  testImplementation("io.insert-koin:koin-test")
  testImplementation("io.insert-koin:koin-test-junit4")

  implementation("com.google.protobuf:protobuf-kotlin:4.36.2")
  implementation("com.google.protobuf:protobuf-java:4.36.2")
  implementation("com.google.protobuf:protobuf-java-util:4.36.2")

  val dropwizardMetricsVersion = "4.2.40"
  api("io.dropwizard.metrics:metrics-core:$dropwizardMetricsVersion")
  api("io.dropwizard.metrics:metrics-jvm:$dropwizardMetricsVersion")

  val floggerVersion = "0.9"
  api("com.google.flogger:flogger:$floggerVersion")
  api("com.google.flogger:flogger-system-backend:$floggerVersion")
  api("com.google.flogger:flogger-log4j2-backend:$floggerVersion")

  val log4j = "2.25.3"
  implementation("org.apache.logging.log4j:log4j:$log4j")
  implementation("org.apache.logging.log4j:log4j-core:$log4j")
  implementation("org.apache.logging.log4j:log4j-api:$log4j")
  implementation("org.slf4j:slf4j-nop:2.0.17")

  implementation("commons-configuration:commons-configuration:1.10")

  val ktorVersion = "3.6.0"
  implementation("io.ktor:ktor-network-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
  implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

  // https://mvnrepository.com/artifact/io.netty/netty-all
  testImplementation("io.netty:netty-all:4.2.19.Final")

  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.4.5")
  testImplementation("com.google.truth.extensions:truth-proto-extension:1.4.5")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
  testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
}

// ktor-server-netty 3.6 depends on Netty's HTTP/3 and QUIC modules, which ship native libraries for
// every platform (~17 MB compressed in the release jar). The server only uses UDP and plain HTTP,
// so keep them out of the jar that every install downloads.
configurations.named("runtimeClasspath") {
  exclude(group = "io.netty", module = "netty-codec-http3")
  exclude(group = "io.netty", module = "netty-codec-native-quic")
  exclude(group = "io.netty", module = "netty-codec-classes-quic")
}

group = "org.emulinker"

description = "EmuLinker-K"

version = "1.0.2"

kotlin { jvmToolchain(17) }

tasks.withType<AbstractCopyTask>().configureEach {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

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
        "useCircularByteArrayBuffer" to true,
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

  named("jmh") { resources { srcDir("src/jmh/resources") } }
}

tasks.named<KotlinCompilationTask<*>>("compileKotlin") {
  dependsOn(":emulinker:generateProto")

  // Filtering the resources has to happen first.
  dependsOn(":emulinker:processResources")

  compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
}

tasks.named("compileTestKotlin") { dependsOn(":emulinker:processTestResources") }

tasks.withType<Test> {
  useJUnitPlatform()
  useJUnit()

  systemProperty(
    "flogger.backend_factory",
    "org.emulinker.testing.TestLoggingBackendFactory#getInstance",
  )
  systemProperty("user.language", "en")
  systemProperty("user.country", "US")
}

// Formatting/linting.
spotless {
  kotlin {
    target("**/*.kt", "**/*.kts")
    targetExclude("bin/", "build/", ".git/", ".idea/", ".mvn", "src/main/java-templates/")
    ktfmt().googleStyle()
  }

  yaml {
    target("**/*.yml", "**/*.yaml")
    targetExclude("build/", ".git/", ".idea/", ".mvn")
    jackson()
  }

  shell {
    target("release/**/*.sh")
    targetExclude("bin/", "build/", ".git/", ".idea/", ".mvn")
  }
}

protobuf {
  protoc { artifact = "com.google.protobuf:protoc:4.36.2" }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        // Generates Kotlin DSL builders.
        id("kotlin") {}
      }
    }
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

tasks.processJmhResources { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

jmh {
  val includePattern =
    if (project.hasProperty("jmhInclude")) project.property("jmhInclude") as String else ".*"
  this@jmh.includes = listOf(includePattern)

  // Run with ./gradlew jmh -PjmhDryRun
  if (project.hasProperty("jmhDryRun")) {
    warmupIterations = 0
    iterations = 1
    fork = 0
    failOnError = true
    benchmarkMode = listOf("ss") // "Single Shot" mode (runs method once, minimal timing overhead)
    resultFormat = "JSON"
  } else if (project.hasProperty("jmhQuick")) {
    // Shorter run for comparing before/after a change: ./gradlew jmh -PjmhQuick
    warmupIterations = 2
    iterations = 3
    fork = 1
  } else {
    profilers = listOf("jfr:dir=build/results/jmh-jfr", "gc")
  }
}

tasks.named("jmh") {
  doLast {
    if (project.hasProperty("jmhDryRun")) {
      val resultsFile = project.layout.buildDirectory.file("results/jmh/results.json").get().asFile
      val json = resultsFile.readText()
      // A simple check: if the JSON is empty or just an empty array "[]", it means no benchmarks
      // ran successfully.
      if (json.replace("\\s+".toRegex(), "") == "[]" || json.isBlank()) {
        throw GradleException(
          "JMH benchmarks failed to produce results (likely due to an exception)."
        )
      }
    }
  }
}

tasks.register<Zip>("buildRelease") {
  group = "distribution"
  description = "Bundles the project into a release zip."

  dependsOn("jar")

  archiveFileName.set("emulinker-k-${project.version}.zip")
  destinationDirectory.set(layout.buildDirectory.dir("distributions"))

  val releaseDir = rootProject.file("release")

  from(releaseDir) {
    include(
      "start-server.sh",
      "stop-server.sh",
      "start-server.bat",
      "stop-server.bat",
      "quickstart.txt",
      "NOTICE.txt",
      "LICENSE.txt",
    )
    into("EmuLinker-K")
  }

  from(releaseDir) {
    include("emulinker.cfg", "log4j2.properties", "language.properties", "access.cfg")
    into("EmuLinker-K/conf")
  }

  from(tasks.jar) { into("EmuLinker-K/lib") }
}
