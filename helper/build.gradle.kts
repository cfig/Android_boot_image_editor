import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.31"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.google.guava:guava:18.0")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-simple:1.7.30")
    implementation("org.apache.commons:commons-exec:1.3")
    implementation("org.bouncycastle:bcprov-jdk15on:1.57")
    implementation("org.apache.commons:commons-compress:1.20")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.12.1")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.12.1")
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    kotlinOptions.jvmTarget = "1.8"
}
