val commonsValidatorVersion: String by project
val consoleVersion: String by project
val jacksonVersion: String by project
val konfVersion: String by project
val kotestVersion: String by project
val kotlinXDateTimeVersion: String by project
val mockkVersion: String by project
val postgresqlVersion: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.allopen") version "2.1.10"
    application
    id("com.gradleup.shadow") version "8.3.6"
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
    mavenLocal()
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/benjishults/console")
        credentials {
            username = providers
                .gradleProperty("github.actor")
                .getOrElse(System.getenv("GITHUB_ACTOR"))
            password = providers
                .gradleProperty("github.token")
                .getOrElse(System.getenv("GITHUB_TOKEN"))
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
    implementation("commons-validator:commons-validator:$commonsValidatorVersion")
    implementation("io.github.benjishults:console:$consoleVersion")
    runtimeOnly("org.postgresql:postgresql:$postgresqlVersion")
    implementation("io.github.nhubbard:konf:$konfVersion")
//    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinXDateTimeVersion")
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
