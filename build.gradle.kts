plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    id("net.fabricmc.fabric-loom-remap") version "1.17.21" apply false
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases")
}

loom {
    splitEnvironmentSourceSets()
    mods {
        register("server_chat_history") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.165.0+26.3")
    "clientCompileOnly"("com.terraformersmc:modmenu:21.0.0")
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.objenesis:objenesis:3.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

sourceSets.test {
    compileClasspath += sourceSets["client"].output
    runtimeClasspath += sourceSets["client"].output
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}

tasks.test {
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-runtime").get().asFile
    doFirst { workingDir.mkdirs() }
    testLogging { events("failed", "skipped") }
}

tasks.jar {
    from("LICENSE") { rename { "LICENSE_server_chat_history" } }
}

base { archivesName.set("server-chat-history") }
tasks.withType<AbstractArchiveTask>().configureEach { archiveVersion.set("${project.version}+mc26.3") }
