import net.fabricmc.loom.api.LoomGradleExtensionAPI
import java.util.Properties

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

subprojects {
    if (name != "common") {
        val target = Properties().apply { file("target.properties").inputStream().use { load(it) } }
        pluginManager.apply(if (target.getProperty("remap").toBoolean()) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
        // 版本模块只声明依赖与源码组合；此处统一产物和开发环境约定。
        val gameVersion = target.getProperty("minecraft")
        val javaVersion = target.getProperty("java").toInt()
        val remapGame = target.getProperty("remap").toBoolean()
        val apiVersion = target.getProperty("fabricApi")
        val menuVersion = target.getProperty("modMenu")

        evaluationDependsOn(":common")
        extensions.configure<BasePluginExtension> { archivesName.set("remember-that-message") }
        repositories { maven("https://maven.terraformersmc.com/releases") }
        val gameSources = extensions.getByType<SourceSetContainer>()
        val gameLoom = extensions.getByType<LoomGradleExtensionAPI>()
        gameLoom.apply {
            splitEnvironmentSourceSets()
            mods.register("remember_that_message") {
                sourceSet(gameSources["main"])
                sourceSet(gameSources["client"])
                modFiles.from(project(":common").tasks.named("jar"))
            }
        }
        dependencies {
            add("minecraft", "com.mojang:minecraft:$gameVersion")
            if (remapGame) add("mappings", gameLoom.officialMojangMappings())
            val implementation = if (remapGame) "modImplementation" else "implementation"
            add(implementation, "net.fabricmc:fabric-loader:0.20.1")
            add(implementation, "net.fabricmc.fabric-api:fabric-api:$apiVersion")
            add(if (remapGame) "modClientCompileOnly" else "clientCompileOnly", "com.terraformersmc:modmenu:$menuVersion") {
                // 这里只编译 Mod Menu 的入口接口，不引入其独立运行依赖。
                isTransitive = false
            }
            add("implementation", project(":common"))
            add("testImplementation", testFixtures(project(":common")))
            add("testImplementation", platform("org.junit:junit-bom:6.0.1"))
            add("testImplementation", "org.junit.jupiter:junit-jupiter")
            add("testImplementation", "org.mockito:mockito-core:5.20.0")
            add("testImplementation", "org.objenesis:objenesis:3.3")
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }
        val adapters = target.getProperty("adapters", "").split(",").filter { it.isNotBlank() }
        val roots = listOf(rootProject.file("platform-shared")) + adapters.map {
            rootProject.file("platform-adapters/$it").also { dir -> require(dir.isDirectory) { "Missing adapter: $it" } }
        }
        for (name in listOf("main", "client", "test")) {
            gameSources[name].apply {
                for (root in roots) {
                    java.srcDir(root.resolve("src/$name/java"))
                    resources.srcDir(root.resolve("src/$name/resources"))
                }
            }
        }
        gameSources["test"].apply {
            compileClasspath += gameSources["client"].output
            runtimeClasspath += gameSources["client"].output
        }
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(javaVersion)
            options.encoding = "UTF-8"
        }
        tasks.named<ProcessResources>("processResources") {
            val modVersion = project.version.toString()
            val metadata = mapOf("version" to modVersion, "minecraft" to gameVersion, "java" to javaVersion, "fabricApi" to apiVersion)
            inputs.properties(metadata)
            filesMatching("fabric.mod.json") { expand(metadata) }
        }
        tasks.withType<AbstractArchiveTask>().configureEach { archiveVersion.set("${project.version}+mc$gameVersion") }
        val commonSources = project(":common").extensions.getByType<SourceSetContainer>()["main"]
        tasks.named<Jar>("jar") {
            from(commonSources.output)
            from(rootProject.file("LICENSE")) { rename { "LICENSE_remember_that_message" } }
        }
        tasks.named<Jar>("sourcesJar") { from(commonSources.allSource) }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            workingDir = layout.buildDirectory.dir("test-runtime").get().asFile
            doFirst { workingDir.mkdirs() }
            testLogging { events("failed", "skipped") }
        }
        tasks.named<ProcessResources>("processClientResources") {
            inputs.property("gameJavaVersion", javaVersion)
            filesMatching("*.mixins.json") { filter { line -> line.replace("JAVA_25", "JAVA_$javaVersion") } }
        }
    }
}
tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }
