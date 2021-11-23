import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    id("java-library")
}

group = "net.sergeych"
version = "0.3.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    testImplementation(kotlin("test"))
    testImplementation("org.postgresql:postgresql:42.2.24")
}

val compileKotlin: KotlinCompile by tasks

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.languageVersion = "1.5"
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name,
            "Implementation-Version" to project.version))
    }
}

java {
    withSourcesJar()
}



tasks.register<Copy>("localRelease") {
    dependsOn("jar")
    from("$rootDir/build/libs/kotyara-$version.jar")
    into("$rootDir/../jarlib")
    rename { "kotyara.jar" }
}

