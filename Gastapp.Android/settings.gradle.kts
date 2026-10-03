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
    }
}

rootProject.name = "Gastapp"

// Telefono (com.binc.gastapp). Sustituye a la app MAUI.
include(":app")

// Reloj Wear OS (mismo applicationId que el telefono).
include(":wear")

// Libreria Android compartida telefono/reloj: red, DTOs y rutas de la Data Layer.
include(":core")

// Logica de negocio pura (Kotlin/JVM): se comparte con el telefono y, despues, con el reloj.
include(":domain")
