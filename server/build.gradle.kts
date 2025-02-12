val commonsValidatorVersion: String by project
val consoleVersion: String by project
val jacksonVersion: String by project
val konfVersion: String by project
val kotestVersion: String by project
val kotlinXDateTimeVersion: String by project
val mockkVersion: String by project
val postgresqlVersion: String by project

plugins {
    alias(libs.plugins.kotlinJvm)
//    alias(libs.plugins.ktor)
    application
}

group = "bps"
version = "1.0-SNAPSHOT"

allOpen {
    annotations("bps.kotlin.Instrumentable")
}

application {
    mainClass.set("bps.budget.Budget")
//    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
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

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
//        freeCompilerArgs.add("-Xcontext-receivers")
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    }
}

dependencies {
//    implementation(projects.shared)
    implementation(libs.logback)
//    implementation(libs.ktor.server.core)
//    implementation(libs.ktor.server.netty)

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

//    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}
