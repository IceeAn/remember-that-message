pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "remember-that-message"

include(":common")
val targets = file("versions").listFiles()!!.filter { it.resolve("target.properties").isFile }
val selected = providers.gradleProperty("gameTargets").orNull?.split(",")?.map { it.trim() }?.toSet()
require(selected == null || selected.isNotEmpty() && selected.all { version -> targets.any { it.name == version } }) {
    "Unknown or empty gameTargets; available: ${targets.map { it.name }.sorted()}"
}
targets.filter { selected == null || it.name in selected }.sortedBy { it.name }.forEach {
    val module = ":mc" + it.name.replace(".", "")
    include(module)
    project(module).projectDir = it
}
