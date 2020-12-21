package init

import org.junit.Test
import org.junit.After
import java.io.File
import java.util.*
import cfig.bootloader_message.BootloaderMsg
import cfig.init.Reboot
import cfig.bootimg.Common.Companion.deleleIfExists

@OptIn(ExperimentalUnsignedTypes::class)
class RebootTest {
    @After
    fun tearDown() {
        File(BootloaderMsg.miscFile).deleleIfExists()
    }

    @Test
    fun testDifferentModes() {
        Reboot.handlePowerctlMessage("reboot,recovery")
        Reboot.handlePowerctlMessage("reboot")
        Reboot.handlePowerctlMessage("reboot,safemode")
        Reboot.handlePowerctlMessage("reboot,dynsystem")
        Reboot.handlePowerctlMessage("reboot,quiescent")
    }

    @Test
    fun shutdown() {
        Reboot.handlePowerctlMessage("shutdown,userrequested")
        Reboot.handlePowerctlMessage("shutdown,thermal")
        Reboot.handlePowerctlMessage("shutdown,thermal,battery")
    }

    @Test
    fun bootloader() {
        Reboot.handlePowerctlMessage("reboot,bootloader")
    }

    @Test
    fun fastboot2bootloader() {
        val props = Properties()
        Reboot.handlePowerctlMessage("reboot,fastboot", props)
    }

    @Test
    fun fastbootd() {
        val props = Properties()
        props.put(Reboot.dynamicPartitionKey, "true")
        Reboot.handlePowerctlMessage("reboot,fastboot", props)
    }

    @Test
    fun fastbootd2() {
        val msg = BootloaderMsg()
        msg.updateBootloaderMessage("boot-fastboot", "recovery", null)
        msg.writeBootloaderMessage()
    }

    @Test
    fun sideload() {
        Reboot.handlePowerctlMessage("reboot,sideload-auto-reboot")
        Reboot.handlePowerctlMessage("reboot,sideload")
    }

    @Test
    fun rescue() {
        val msg = BootloaderMsg()
        msg.updateBootloaderMessage("boot-rescue", "recovery", null)
        msg.writeBootloaderMessage()
    }
}
