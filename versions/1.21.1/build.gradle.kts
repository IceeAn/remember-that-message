plugins {
    id("net.fabricmc.fabric-loom-remap")
}

version = rootProject.version
group = rootProject.group
base { archivesName.set("server-chat-history") }

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
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.20.1")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.17+1.21.1")
    "modClientCompileOnly"("com.terraformersmc:modmenu:11.0.5")
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.objenesis:objenesis:3.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Share existing code unless this version supplies a replacement at the same path.
// The full platform abstraction is a separate, measured follow-up to this port.
fun sharedJava(name: String): SourceDirectorySet {
    val overrides = file("src/$name/java")
    val replaced = fileTree(overrides).matching { include("**/*.java") }.files
        .map { it.relativeTo(overrides).invariantSeparatorsPath }
    return objects.sourceDirectorySet("shared${name.replaceFirstChar { it.uppercase() }}", "Shared $name Java")
        .apply { srcDir(rootProject.file("src/$name/java")); exclude(replaced) }
}

sourceSets.main { java.source(sharedJava("main")) }
sourceSets["client"].apply { java.source(sharedJava("client")); resources.srcDir(rootProject.file("src/client/resources")) }
sourceSets.test {
    java.source(sharedJava("test"))
    resources.srcDir(rootProject.file("src/test/resources"))
    compileClasspath += sourceSets["client"].output
    runtimeClasspath += sourceSets["client"].output
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21); options.encoding = "UTF-8" }
tasks.processResources {
    from(rootProject.file("src/main/resources")) { exclude("fabric.mod.json") }
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}
tasks.withType<AbstractArchiveTask>().configureEach { archiveVersion.set("${project.version}+mc1.21.1") }
tasks.jar { from(rootProject.file("LICENSE")) { rename { "LICENSE_server_chat_history" } } }
tasks.test {
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-runtime").get().asFile
    doFirst { workingDir.mkdirs() }
    testLogging { events("failed", "skipped") }
}

tasks.named<ProcessResources>("processClientResources") {
    filesMatching("*.mixins.json") { filter { line -> line.replace("JAVA_25", "JAVA_21") } }
}
