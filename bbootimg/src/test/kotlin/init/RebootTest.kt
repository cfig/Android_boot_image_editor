package init

import cfig.init.Reboot
import org.junit.Test
import java.util.*

@ExperimentalUnsignedTypes
class RebootTest {
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
    fun fastbootd() {
        Reboot.handlePowerctlMessage("reboot,bootloader")
        val props = Properties()
        Reboot.handlePowerctlMessage("reboot,fastboot", props)
        props.put(Reboot.dynamicPartitionKey, "true")
        Reboot.handlePowerctlMessage("reboot,fastboot", props)
    }

    @Test
    fun sideload() {
        Reboot.handlePowerctlMessage("reboot,sideload-auto-reboot")
        Reboot.handlePowerctlMessage("reboot,sideload")
    }
}
