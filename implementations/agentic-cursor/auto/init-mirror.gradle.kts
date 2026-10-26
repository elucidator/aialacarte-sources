allprojects {
    buildscript {
        repositories {
            maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            mavenCentral()
        }
    }
    repositories {
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
}

settingsEvaluated {
    pluginManagement.repositories.apply {
        clear()
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        mavenCentral()
    }
}
