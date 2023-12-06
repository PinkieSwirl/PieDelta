plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
    // id("com.autonomousapps.dependency-analysis")
}

// main
val kotlinVersion: String by project
val kotlinSerializationVersion: String by project
// test
val junitVersion: String by project
val jqwikVersion: String by project
// quality
val detektVersion: String by project

group = "eu.pieland"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:$detektVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinSerializationVersion")

    testImplementation(kotlin("test:$kotlinVersion"))

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")

    testImplementation("net.jqwik:jqwik-api:$jqwikVersion")
    testImplementation("net.jqwik:jqwik-kotlin:$jqwikVersion")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
    }
}

kotlin {
    explicitApi()
    jvmToolchain(8)
}

//dependencyAnalysis {
//    issues {
//        all {
//            onAny {
//                exclude("org.jetbrains.kotlin:kotlin-test")
//            }
//        }
//    }
//}
