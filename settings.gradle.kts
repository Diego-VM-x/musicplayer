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

rootProject.name = "LarkClean"

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")

include(":feature:player")
include(":feature:library")
include(":feature:lyrics")
include(":feature:equalizer")
include(":feature:backup")
include(":feature:settings")
include(":feature:widget")
