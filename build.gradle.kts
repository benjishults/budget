val kotestVersion: String by project
val jacksonVersion: String by project
val konfVersion: String by project
val mockkVersion: String by project
val consoleVersion: String by project
val gitHubActor: String =
    providers
        .gradleProperty("github.actor")
        .getOrElse(System.getenv("GITHUB_ACTOR"))
val gitHubToken: String =
    providers
        .gradleProperty("github.token")
        .getOrElse(System.getenv("GITHUB_TOKEN"))

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "bps"
version = "1.0-SNAPSHOT"

allOpen {
    annotations("bps.kotlin.Instrumentable")
}

application {
    mainClass = "bps.budget.Budget"
}

repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/benjishults/console")
        credentials {
            username = gitHubActor
            password = gitHubToken
        }
    }
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
    // password hashing https://javadoc.io/doc/de.mkammerer/argon2-jvm/2.6/de/mkammerer/argon2/Argon2.html
    //   https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#argon2id
//    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("commons-validator:commons-validator:1.9.0")
    implementation("io.github.benjishults:console:$consoleVersion")
    runtimeOnly("org.postgresql:postgresql:42.7.3")
    implementation("io.github.nhubbard:konf:$konfVersion")
//    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("io.github.benjishults:console-test:$consoleVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
