// Copyright 2021 yuyezhong@gmail.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
    `java-library`
    application
}

repositories {
    mavenCentral()
}

dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    }
    //kotlin
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("cc.cfig:io:0.2")
    implementation("com.google.guava:guava:33.0.0-jre")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.apache.commons:commons-exec:1.3")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70") //org.bouncycastle.pkcs
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("org.tukaani:xz:1.9")
    implementation("com.github.freva:ascii-table:1.2.0")
    implementation("com.nimbusds:nimbus-jose-jwt:9.31")
    // jackson
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.14.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.13.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions {
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "-opt-in=kotlin.ExperimentalUnsignedTypes"
        jvmTarget = "11"
    }
}

application {
    mainClass.set("cfig.helper.LauncherKt")
}
tasks {
    jar {
        manifest {
            attributes["Implementation-Title"] = "Helper"
            attributes["Main-Class"] = "cfig.helper.LauncherKt"
        }
        from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
        excludes.addAll(mutableSetOf("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    test {
        testLogging {
            showExceptions = true
            showStackTraces = true
        }
    }
}
