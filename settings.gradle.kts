pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "server-chat-history"

include(":mc1211")
project(":mc1211").projectDir = file("versions/1.21.1")
