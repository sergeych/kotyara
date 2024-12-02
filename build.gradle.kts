import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.0"
    id("java-library")
    `maven-publish`
}

group = "net.sergeych"
version = "1.5.3-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://maven.universablockchain.com/")
    maven("https://gitea.sergeych.net/api/packages/SergeychWorks/maven")
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    api("net.sergeych:mp_stools:[1.3.3,)")
    implementation("net.sergeych:boss-serialization-mp:0.2.10")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    api("net.sergeych:mp_bintools:0.1.9-SNAPSHOT")
    implementation("com.ionspin.kotlin:bignum:0.3.8")
    testImplementation(kotlin("test"))
    testImplementation("org.postgresql:postgresql:42.5.1")
    testImplementation("com.h2database:h2:2.2.220")
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

tasks.named<KotlinCompilationTask<*>>("compileKotlin").configure {
    compilerOptions.freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    compilerOptions.freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    compilerOptions.freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
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
            val mavenUser: String? by project
            val mavenPassword: String? by project
            url = uri("https://maven.universablockchain.com/")
            if (mavenUser != null && mavenPassword != null) {
                credentials {
                    username = mavenUser
                    password = mavenPassword
                }
            } else {
                println("You can't publish from this computer")
            }
        }
//
//            credentials {
//                username = System.getenv("maven_user")
//                password = System.getenv("maven_password")
//            }
    }
}


