pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "server-chat-history"

include(":common", ":mc263", ":mc1211")
project(":mc263").projectDir = file("versions/26.3")
project(":mc1211").projectDir = file("versions/1.21.1")

include(":mc1201")
project(":mc1201").projectDir = file("versions/1.20.1")
