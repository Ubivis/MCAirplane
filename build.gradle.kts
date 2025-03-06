plugins {
    kotlin("jvm") version "1.8.0"
    id("io.papermc.paperweight.userdev") version "1.5.8"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.ubivismedia.aircraft"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:1.20.1-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.39.3.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

tasks {
    shadowJar {
        archiveClassifier.set("all")
    }
    build {
        dependsOn("shadowJar")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
