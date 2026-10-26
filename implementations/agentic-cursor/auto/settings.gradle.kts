pluginManagement {
    repositories {
        maven {
            url = uri("https://repo.huaweicloud.com/repository/maven/")
            content {
                includeGroupByRegex(".*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        mavenCentral()
    }
}

rootProject.name = "conference-planner"
