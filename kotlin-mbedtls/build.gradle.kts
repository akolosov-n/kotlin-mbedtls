import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    id("java-library")
    id("maven-publish")
    id("com.github.mfarsikov.kewt-versioning") version "1.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
}
version = kewtVersioning.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("net.java.dev.jna:jna:5.10.0")

    // TESTS
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("ch.qos.logback:logback-classic:1.2.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

kewtVersioning.configuration {
    separator = ""
}

// --- PUBLISHING ---
val repoName = System.getenv("GITHUB_REPOSITORY") ?: "open-coap/kotlin-mbedtls"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/$repoName")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("default") {
            from(components["java"])
            groupId = "com.github." + repoName.replace('/', '.')
            pom {
                name.set("Kotlin mbedtls")
                description.set("Bridge of mbedtls and jvm (kotlin)")
                url.set("https://github.com/$repoName")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}