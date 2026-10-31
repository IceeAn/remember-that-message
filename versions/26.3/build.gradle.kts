plugins { id("net.fabricmc.fabric-loom") }

evaluationDependsOn(":common")
base { archivesName.set("server-chat-history") }
repositories { maven("https://maven.terraformersmc.com/releases") }
loom {
    splitEnvironmentSourceSets()
    mods {
        register("server_chat_history") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
            // 将公共资源同时登记为开发环境的模组资源根。
            modFiles.from(project(":common").tasks.named("jar"))
        }
    }
}
dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.165.0+26.3")
    "clientCompileOnly"("com.terraformersmc:modmenu:21.0.0")
    implementation(project(":common"))
    testImplementation(testFixtures(project(":common")))
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.objenesis:objenesis:3.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Explicit shared platform sources; no version inherits another version's files.
sourceSets.main {
    java.srcDir(rootProject.file("platform-shared/src/main/java"))
    resources.srcDir(rootProject.file("platform-shared/src/main/resources"))
}
sourceSets["client"].apply {
    java.srcDir(rootProject.file("platform-shared/src/client/java"))
    resources.srcDir(rootProject.file("platform-shared/src/client/resources"))
}
sourceSets.test {
    java.srcDir(rootProject.file("platform-shared/src/test/java"))
    resources.srcDir(rootProject.file("platform-shared/src/test/resources"))
    compileClasspath += sourceSets["client"].output
    runtimeClasspath += sourceSets["client"].output
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
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
tasks.withType<AbstractArchiveTask>().configureEach { archiveVersion.set("${project.version}+mc26.3") }
val commonSources = project(":common").extensions.getByType<SourceSetContainer>()["main"]
tasks.jar {
    from(commonSources.output)
    from(rootProject.file("LICENSE")) { rename { "LICENSE_server_chat_history" } }
}
tasks.named<Jar>("sourcesJar") { from(commonSources.allSource) }
tasks.test {
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-runtime").get().asFile
    doFirst { workingDir.mkdirs() }
    testLogging { events("failed", "skipped") }
}
