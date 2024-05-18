import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    `java-gradle-plugin`
    `maven-publish`
    `kotlin-dsl`
}

group = "dev.openrune"
version = "1.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("joda-time:joda-time:2.12.2")
    implementation("com.beust:klaxon:5.5")
    implementation("com.google.code.gson:gson:2.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation("io.ktor:ktor-client-core:2.0.3")
    implementation("io.ktor:ktor-client-cio:2.0.3")
    implementation("io.ktor:ktor-client-logging:2.0.3")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    implementation("io.github.microutils:kotlin-logging:2.0.10")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("commons-net:commons-net:3.10.0")

}


java {
    // Configure the Java toolchain to use Java 11
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType<JavaCompile> {
    // Set source and target compatibility to Java 11
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.jar {
    // Ensure the Jar task includes the plugin descriptor
    from(sourceSets.main.get().resources.matching {
        include("META-INF/gradle-plugins/*.properties")
    })
}

gradlePlugin {
    plugins {
        create("bootstrap") {
            id = "dev.openrune.bootstrap"
            implementationClass = "dev.openrune.BootstrapPlugin"
        }
    }
}

publishing {
    repositories {

        maven {
            url = uri("$buildDir/repo")
        }
        if (System.getenv("REPO_URL") != null) {
            maven {
                url = uri(System.getenv("REPO_URL"))
                credentials {
                    username = System.getenv("REPO_USERNAME")
                    password = System.getenv("REPO_PASSWORD")
                }
            }
        }
    }


    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

