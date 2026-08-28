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
    }
}

rootProject.name = "NeonTidesNativeAndroid"
include(":app")

// La build MLC genera dist/lib/mlc4j. In assenza del runtime nativo usiamo
// soltanto lo stub di compilazione: la UI segnalerà che MLC non è disponibile.
include(":mlc4j")
val generatedMlc4j = file("dist/lib/mlc4j")
project(":mlc4j").projectDir = if (generatedMlc4j.exists()) {
    generatedMlc4j
} else {
    file("mlc4j-stub")
}
