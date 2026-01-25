pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Mapbox Maven repository
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                // Get your secret token from https://account.mapbox.com/access-tokens/
                // It should start with "sk."
                password =
                    "sk.eyJ1IjoibW9yZWhhcnNoIiwiYSI6ImNta3NveXc1MDFiZW0zZnNlNXJxa3V4bGQifQ.M7jecbq64j_dNuwkX4NbQg"
            }
        }
    }
}

rootProject.name = "DASH_MAP"
include(":app")