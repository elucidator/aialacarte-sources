rootProject.name = "conference-planner"

// Maven Central and the Gradle plugin portal are the defaults. On networks where Central is
// blocked, point the build at a mirror that proxies it:
//   export MAVEN_REPOSITORY_URL=https://mirror.example/maven
val mirror: String? = System.getenv("MAVEN_REPOSITORY_URL")?.takeIf { it.isNotBlank() }

pluginManagement {
    repositories {
        System.getenv("MAVEN_REPOSITORY_URL")?.takeIf { it.isNotBlank() }?.let { maven(it) }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        if (mirror == null) mavenCentral() else maven(mirror)
    }
}
