group = "ru.bravik"
version = "0.0.1"

val ktor_version: String = "2.3.5"

plugins {
    kotlin("jvm") version "1.9.21"
    // For building fat jar
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    // For kotlin-telegram-bot
    maven("https://jitpack.io")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "ru.bravik.MainKt"
        }
        configurations["compileClasspath"].forEach { file: File ->
            from(zipTree(file.absoluteFile))
        }
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    shadowJar {
        archiveBaseName.set("consul")
        archiveClassifier.set("")
        archiveVersion.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        manifest {
            attributes["Main-Class"] = "ru.bravik.MainKt"
        }
    }
}

dependencies {
    // Http client
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.12")

    // HTML page parser
    implementation("org.jsoup:jsoup:1.17.1")

    // Anti-captcha client
    implementation("com.github.2captcha:2captcha-java:1.1.1")

    // For reading file from end of the line
    implementation("commons-io:commons-io:2.11.0")

    // Need to add this explicitly, because otherwise kotlin-telegram-bot throws errors
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")

    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
}