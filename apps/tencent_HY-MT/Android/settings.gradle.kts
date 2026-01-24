pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Try adding JitPack as an alternative repository
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "tencent_HY-MT"
include(":app")
