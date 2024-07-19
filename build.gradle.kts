import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotestVersion = "5.9.1"
val jacksonVersion = "2.17.2"
val konfVersion = "1.1.2"
val mockkVersion = "1.13.12"

plugins {
    kotlin("jvm") version "2.0.0"
}

group = "benjishults"
version = "1.0-SNAPSHOT"

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

//    implementation("com.uchuhimo:konf:$konfVersion")
    implementation("com.uchuhimo:konf-core:$konfVersion")
    implementation("com.uchuhimo:konf-yaml:$konfVersion")
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
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
