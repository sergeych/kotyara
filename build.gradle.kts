import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    id("java-library")
}

group = "net.sergeych"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.postgresql:postgresql:42.2.20")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
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



//val kotyaraJars by configurations.creating {
//    isCanBeConsumed = true
//    isCanBeResolved = false
//    // If you want this configuration to share the same dependencies, otherwise omit this line
//    extendsFrom(configurations["implementation"], configurations["runtimeOnly"])
//}

//artifacts {
//    add("kotyara", kotyaraJars)
//}
//

