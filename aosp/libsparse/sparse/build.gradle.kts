plugins {
    `cpp-library`
}

library {
    targetMachines.set(listOf(machines.linux.x86_64, machines.macOS.x86_64))
    linkage.set(listOf(Linkage.STATIC))
    dependencies {
        implementation(project(":aosp:libsparse:base"))
    }
}

tasks.withType(CppCompile::class.java).configureEach {
    macros.put("NDEBUG", null)
    compilerArgs.add("-Wall")
    compilerArgs.add("-std=c++17")
}

tasks.withType(LinkSharedLibrary::class.java).configureEach {
    linkerArgs.add("-lz")
}

tasks.withType(CreateStaticLibrary::class.java).configureEach {
}
