plugins {
    `cpp-library`
}

library {
    targetMachines.set(listOf(machines.linux.x86_64, machines.macOS.x86_64))
    linkage.set(listOf(Linkage.STATIC))
}

extensions.configure<CppLibrary> {
    source.from(file("src/main/cpp"))
    privateHeaders.from(file("src/main/headers"))
    publicHeaders.from(file("src/main/public"))
}

tasks.withType(CppCompile::class.java).configureEach {
    compilerArgs.add("-std=c++17")
}
