import org.gradle.kotlin.dsl.register

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.1"
}

val jarName = "northstar.jar"
group = "dev.rosalyn"
version = "0.0.1"

apply(from = "schema.gradle.kts")

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io/")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.exposed:exposed-core:1.1.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.1.1")
    implementation("org.jetbrains.exposed:exposed-json:1.1.1")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("io.github.lilrosalyn.commando:common:3.0.6")
    implementation("ch.qos.logback:logback-classic:1.5.8")
    implementation("ch.qos.logback:logback-core:1.5.8")
    implementation("net.dv8tion:JDA:6.2.1")
    implementation("cc.ekblad:4koma:1.2.0")
    implementation(kotlin("reflect"))
}

java.sourceSets["main"].kotlin {
    srcDir("build/generated/main/kotlin")
}

tasks.register<JavaExec>("run") {
    dependsOn("shadowJar")
    standardInput = System.`in`
    classpath = files("${layout.buildDirectory.asFile.get().path}/libs/$jarName")
    workingDir = file("run/")
    workingDir.mkdirs()
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "dev.rosalyn.northstar.MainKt")
    }
}

tasks.shadowJar {
    archiveFileName = jarName
}

kotlin {
    jvmToolchain(21)
}