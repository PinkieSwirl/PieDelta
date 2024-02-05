plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

group = "eu.pieland"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.4")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik-kotlin:1.8.2")
}

tasks.test {
    minHeapSize = "2g"
    maxHeapSize = "4g"
    jvmArgs = listOf("-XX:MaxPermSize=1g")
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
        excludeTags("slow")
    }
}

kotlin {
    explicitApi()
    jvmToolchain(8)
}
