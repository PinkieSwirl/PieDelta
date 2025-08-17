plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
//    id("com.autonomousapps.dependency-analysis")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    jacoco
}

// main
val kotlinVersion: String by project
val kotlinSerializationVersion: String by project
// test
val junitVersion: String by project
val jqwikVersion: String by project
val jimfsVersion: String by project
// quality
val jacocoVersion: String by project
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
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")

    testImplementation("net.jqwik:jqwik-api:$jqwikVersion")
    testImplementation("net.jqwik:jqwik-kotlin:$jqwikVersion")
    testImplementation("com.google.jimfs:jimfs:$jimfsVersion")
}

tasks.test {
    minHeapSize = "8g"
    maxHeapSize = "16g"
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
        excludeTags("slow")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report

    reports {
        xml.required = true
        html.required = false
    }
}

jacoco {
    toolVersion = jacocoVersion
}

kotlin {
    jvmToolchain(8)
}

//dependencyAnalysis {
//    usage {
//        analysis {
//            checkSuperClasses(true) // false by default
//        }
//    }
//}
