plugins {
    `cpp-application`
}

application {
    targetMachines.set(listOf(machines.linux.x86_64, machines.macOS.x86_64))
}

tasks.withType(CppCompile::class.java).configureEach {
    macros.put("__ANDROID_VNDK__", null)
    //macros.put("CFIG_NO_FIX_STAT", 1)
    compilerArgs.add("-std=c++17")
    compilerArgs.add("-Wno-write-strings")
}
