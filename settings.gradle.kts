pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "localWiroKit"
            url =
                uri(
                    System.getProperty("maven.repo.local")
                        ?: "${System.getProperty("user.home")}/.m2/repository",
                )
            content {
                includeGroup("ai.wiro")
            }
        }
    }
}

rootProject.name = "Kotlin-QuickStart"

include(":app")
include(":wirokit")
include(":consumer-fixture")
