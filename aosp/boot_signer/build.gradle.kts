import org.gradle.jvm.tasks.Jar

plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
}

val fatJar = task("fatJar", type = Jar::class) {
    manifest {
        attributes["Implementation-Title"] = "AOSP boot signer"
        attributes["Main-Class"] = "com.android.verity.BootSignature"
    }
    from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
    excludes.addAll(mutableSetOf("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    with(tasks.jar.get() as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}
