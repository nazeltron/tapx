plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "dev.tapx"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

// Paper runs Mojang-mapped since 1.20.5, so no reobfuscation is needed.
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set("TapX")
    archiveClassifier.set("")
    relocate("kotlin", "dev.tapx.libs.kotlin")
    relocate("org.jetbrains.annotations", "dev.tapx.libs.annotations")
    relocate("org.intellij.lang.annotations", "dev.tapx.libs.intellij")
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
}

tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }

// Render the game screen to PNGs (build/preview) without a server: ./gradlew preview
tasks.register<JavaExec>("preview") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.tapx.PreviewKt")
}
tasks.register<JavaExec>("dumpMapColors") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.tapx.DumpMapColorsKt")
    args(project.findProperty("out") ?: "build/mapcolors.json")
}

// src/test only holds dev tools (preview, colour dump), not unit tests.
tasks.test { failOnNoDiscoveredTests.set(false) }
