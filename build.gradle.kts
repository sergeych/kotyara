import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    id("java-library")
}

group = "net.sergeych"
version = "0.1.5"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.postgresql:postgresql:42.3.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
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

