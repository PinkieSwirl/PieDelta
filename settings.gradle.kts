pluginManagement {
    plugins {
        val kotlinVersion: String by settings
        val detektVersion: String by settings
        val daVersion: String by settings
        val bcvVersion: String by settings
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("io.gitlab.arturbosch.detekt") version detektVersion
        id("com.autonomousapps.dependency-analysis") version daVersion
        id("org.jetbrains.kotlinx.binary-compatibility-validator") version bcvVersion
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "PieDelta"
