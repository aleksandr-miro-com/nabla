pluginManagement {
    repositories {
        maven {
            setUrl("https://artifactory.tools.devrtb.com/artifactory/java-virtual")
            credentials {
                val artifactoryUser: String? by settings
                val artifactoryPassword: String? by settings
                username = artifactoryUser ?: System.getenv("ARTIFACTORY_USER")
                password = artifactoryPassword ?: System.getenv("ARTIFACTORY_PASSWORD")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            setUrl("https://artifactory.tools.devrtb.com/artifactory/java-virtual")
            credentials {
                val artifactoryUser: String? by settings
                val artifactoryPassword: String? by settings
                username = artifactoryUser ?: System.getenv("ARTIFACTORY_USER")
                password = artifactoryPassword ?: System.getenv("ARTIFACTORY_PASSWORD")
            }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "nabla"

include(":nabla", ":playground", ":backend")
