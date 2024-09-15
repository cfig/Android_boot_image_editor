plugins {
    `cpp-application`
}

application {
    targetMachines.set(listOf(machines.linux.x86_64, machines.macOS.x86_64))
    dependencies {
        implementation(project(":aosp:libsparse:sparse"))
    }
}

tasks.withType(LinkExecutable::class.java).configureEach {
    linkerArgs.add("-lz")
}

tasks.withType(CppCompile::class.java).configureEach {
    compilerArgs.add("-std=c++17")
}
