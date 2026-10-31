plugins {
    base
    id("net.fabricmc.fabric-loom") version "1.17.21" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17.21" apply false
}

allprojects {
    version = providers.gradleProperty("mod_version").get()
    group = providers.gradleProperty("maven_group").get()
    repositories { mavenCentral() }
}

tasks.named("build") { dependsOn(":common:build", ":mc263:build", ":mc1211:build") }
tasks.named("clean") { dependsOn(":common:clean", ":mc263:clean", ":mc1211:clean") }
