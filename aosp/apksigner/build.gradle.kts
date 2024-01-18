plugins {
    java
    application
}

version = "1.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.android.signapk.SignApk")
}

dependencies {
    implementation(project(":aosp:bouncycastle:bcpkix"))
    implementation(project(":aosp:bouncycastle:bcprov"))
}

tasks {
    jar {
        manifest {
            attributes["Implementation-Title"] = "AOSP ApkSigner"
            attributes["Main-Class"] = "com.android.signapk.SignApk"
        }
        from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
        excludes.addAll(mutableSetOf("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(":aosp:bouncycastle:bcpkix:jar")
    }
    test {
        testLogging {
            showExceptions = true
            showStackTraces = true
        }
    }
}
