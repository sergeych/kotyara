//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20"
    id("java-library")
    `maven-publish`
}

group = "net.sergeych"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://maven.universablockchain.com/")
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("net.sergeych:mp_stools:1.2.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("org.postgresql:postgresql:42.3.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        )
    }
}

java {
    withSourcesJar()
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(8, "seconds")
}


//tasks.register<Copy>("localRelease") {
//    dependsOn("jar")
//    from("$rootDir/build/libs/kotyara-$version.jar")
//    into("$rootDir/../jarlib")
//    rename { "kotyara.jar" }
//}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            url = uri("https://maven.universablockchain.com/")
            credentials {
                username = System.getenv("maven_user")
                password = System.getenv("maven_password")
            }
        }
    }

}

