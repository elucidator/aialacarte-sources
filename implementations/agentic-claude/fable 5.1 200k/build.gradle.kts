plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.conferenceplanner"
version = "1.0.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.microsoft.playwright:playwright:1.63.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveFileName = "conference-planner.jar"
}

// Default checks: API, transaction and concurrency tests against a Testcontainers PostgreSQL.
tasks.test {
    useJUnitPlatform { excludeTags("two-process", "ui") }
    testLogging { events("passed", "failed", "skipped") }
}

// Two JVM processes sharing one PostgreSQL race for the last place.
val twoProcessTest by tasks.registering(Test::class) {
    description = "Last-place race across two app processes sharing one database"
    group = "verification"
    dependsOn(tasks.bootJar)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("two-process") }
    systemProperty("conference.jar", tasks.bootJar.get().archiveFile.get().asFile.absolutePath)
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}

// Browser test (Playwright driving a locally installed Chrome; override with -Pbrowser=chromium|msedge).
val uiTest by tasks.registering(Test::class) {
    description = "Browser flow test of the served HTML UI"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("ui") }
    systemProperty("ui.browserChannel", (project.findProperty("browser") as String?) ?: "chrome")
    systemProperty("ui.screenshotDir", layout.buildDirectory.dir("ui-screenshots").get().asFile.absolutePath)
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
