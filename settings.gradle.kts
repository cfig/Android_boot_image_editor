pluginManagement {
    plugins {
        kotlin("jvm") version "2.1.21"
    }
}
rootProject.name = "boot"
include("bbootimg")
include("aosp:apksigner")
include("aosp:boot_signer")
include("aosp:bouncycastle:bcpkix")
include("aosp:bouncycastle:bcprov")
//include("aosp:libavb1.1")
//include("aosp:libavb1.2")
//include("avbImpl")
include("helper")
include("lazybox")

