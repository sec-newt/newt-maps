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
        // The shared Newt library (theme, fonts, test helpers). GitHub Packages needs a login
        // even for public packages; CI passes its own token. exclusiveContent: Newt packages
        // come only from here, and nothing else is looked up here.
        exclusiveContent {
            forRepository {
                maven("https://maven.pkg.github.com/sec-newt/newt-android-lib") {
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
            filter { includeGroup("io.github.secnewt") }
        }
    }
}

rootProject.name = "NewtTemplate"
include(":app")
