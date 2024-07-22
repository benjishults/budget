import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotestVersion = "5.9.1"
val jacksonVersion = "2.17.2"
val konfVersion = "2.1.0"
val mockkVersion = "1.13.12"

plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "benjishults"
version = "1.0-SNAPSHOT"

application {
    mainClass = "bps.budget.Budget"
}

repositories {
    mavenCentral()
}

//kotlin {
//    jvmToolchain(21)
//}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
//        freeCompilerArgs.add("-Xcontext-receivers")
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {

//    implementation("io.github.nhubbard:konf:$konfVersion")
//    implementation("io.github.config4k:config4k:0.7.0")
//    implementation("io.github.nhubbard:konf-core:$konfVersion")
//    implementation("io.github.nhubbard:konf-yaml:$konfVersion")
    implementation("io.github.nhubbard:konf:$konfVersion")
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
//    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
