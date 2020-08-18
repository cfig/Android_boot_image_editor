import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.0"
    kotlin("plugin.serialization") version "1.4.0"
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.slf4j:slf4j-simple:1.7.30")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.11.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0-RC")
    implementation("com.google.guava:guava:18.0")
    implementation("org.apache.commons:commons-exec:1.3")
    implementation("org.apache.commons:commons-compress:1.16.1")
    implementation("commons-codec:commons-codec:1.11")
    implementation("junit:junit:4.12")
    implementation("org.bouncycastle:bcprov-jdk15on:1.57")
    implementation("de.vandermeer:asciitable:0.3.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    mainClassName = "cfig.packable.PackableLauncherKt"
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks {
    jar {
        manifest {
            attributes["Implementation-Title"] = "Android image reverse engineering toolkit"
            attributes["Main-Class"] = "cfig.packable.PackableLauncherKt"
        }
        from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
        excludes.addAll(mutableSetOf("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA"))
    }
    test {
        testLogging {
            showExceptions = true
            showStackTraces = true
        }
    }
}
