plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "8.3.9"
}

group = "dev.tapx"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

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
}

tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
