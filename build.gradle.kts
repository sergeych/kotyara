//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.21"
    id("java-library")
    `maven-publish`
}

group = "net.sergeych"
version = "1.3.3"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.universablockchain.com/")
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("net.sergeych:mp_stools:[1.3.3,)")
    implementation("net.sergeych:boss-serialization-mp:0.2.9")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("com.ionspin.kotlin:bignum:0.3.8")
    testImplementation(kotlin("test"))
    testImplementation("org.postgresql:postgresql:42.5.1")
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

//configurations.all {
//    resolutionStrategy.cacheChangingModulesFor(8, "seconds")
//}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            val mavenUser: String by project
            val mavenPassword: String by project
            url = uri("https://maven.universablockchain.com/")
            credentials {
                username = mavenUser
                password = mavenPassword
            }
//
//            credentials {
//                username = System.getenv("maven_user")
//                password = System.getenv("maven_password")
//            }
        }
    }

}

