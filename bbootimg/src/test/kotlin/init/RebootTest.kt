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

package init

import org.junit.Test
import org.junit.After
import java.io.File
import java.util.*
import cfig.bootloader_message.BootloaderMsg
import cfig.init.Reboot
import cfig.bootimg.Common.Companion.deleleIfExists
import org.slf4j.LoggerFactory

@OptIn(ExperimentalUnsignedTypes::class)
class RebootTest {
    private val log = LoggerFactory.getLogger(RebootTest::class.java)

    @After
    fun tearDown() {
        File(BootloaderMsg.miscFile).deleleIfExists()
    }

    @Test
    fun testDifferentModes() {
        Reboot.handlePowerctlMessage("reboot")
        Reboot.handlePowerctlMessage("reboot,safemode")
        Reboot.handlePowerctlMessage("reboot,dynsystem")
        Reboot.handlePowerctlMessage("reboot,cold")
        Reboot.handlePowerctlMessage("reboot,ota")
        Reboot.handlePowerctlMessage("reboot,factory_reset")
        Reboot.handlePowerctlMessage("reboot,shell")
        Reboot.handlePowerctlMessage("reboot,adb")
        Reboot.handlePowerctlMessage("reboot,userrequested")
        Reboot.handlePowerctlMessage("reboot,rescueparty")
        Reboot.handlePowerctlMessage("reboot,powerloss")
        Reboot.handlePowerctlMessage("reboot,undervoltage")
        Reboot.handlePowerctlMessage("reboot,tool") //151
        Reboot.handlePowerctlMessage("reboot,wdt") //152
        Reboot.handlePowerctlMessage("reboot,unknown") //153

        Reboot.handlePowerctlMessage("reboot,quiescent") //170
        Reboot.handlePowerctlMessage("reboot,rtc") //171
        Reboot.handlePowerctlMessage("reboot,dm-verity_device_corrupted") //172
        Reboot.handlePowerctlMessage("reboot,dm-verity_enforcing") //173

        Reboot.handlePowerctlMessage("reboot,userrequested,fastboot") //178
        Reboot.handlePowerctlMessage("reboot,userrequested,recovery") //179
        Reboot.handlePowerctlMessage("reboot,userrequested,recovery,ui") //180
    }

    @Test
    fun shutdown() {
        Reboot.handlePowerctlMessage("shutdown")
        Reboot.handlePowerctlMessage("shutdown,userrequested")
        Reboot.handlePowerctlMessage("shutdown,thermal")
        Reboot.handlePowerctlMessage("shutdown,battery")
        Reboot.handlePowerctlMessage("shutdown,container")
        Reboot.handlePowerctlMessage("shutdown,thermal,battery")
        Reboot.handlePowerctlMessage("shutdown,suspend") // Suspend to RAM
        Reboot.handlePowerctlMessage("shutdown,hibernate") // Suspend to DISK
        Reboot.handlePowerctlMessage("shutdown,userrequested,fastboot")
        Reboot.handlePowerctlMessage("shutdown,userrequested,recovery")
    }

    @Test
    fun bootloader() {
        Reboot.handlePowerctlMessage("reboot,bootloader")
    }

    @Test
    fun fastbootd() {
        log.info("fastbootd test 1")
        Reboot.handlePowerctlMessage("reboot,fastboot",
            Properties().apply {
                put(Reboot.dynamicPartitionKey, "true")
            })
        log.info("fastbootd test 2")
        BootloaderMsg().let {
            it.updateBootloaderMessage("boot-fastboot", "recovery", null)
            it.writeBootloaderMessage()
        }
        log.info("fastbootd test 3: not supported, change to bootloader")
        Reboot.handlePowerctlMessage("reboot,fastboot", Properties())
    }

    @Test
    fun recovery() {
        log.info("recovery test 1")
        Reboot.handlePowerctlMessage("reboot,recovery")
    }

    @Test
    fun recovery_rescue() {
        log.info("recovery test 1: rescue")
        BootloaderMsg().let {
            it.updateBootloaderMessage("boot-rescue", "recovery", null)
            it.writeBootloaderMessage()
        }
        log.info("recovery test 2: rescue")
        Reboot.handlePowerctlMessage("reboot,rescue")
    }

    @Test
    fun recovery_sideload() {
        log.info("recovery test 3: sideload")
        Reboot.handlePowerctlMessage("reboot,sideload-auto-reboot")
        Reboot.handlePowerctlMessage("reboot,sideload")
    }
}
