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

package bcb

import cfig.bcb.BootloaderMsg
import cfig.bootimg.Common.Companion.deleleIfExists
import org.junit.After
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File

class BootloaderMsgTest {
    private val log = LoggerFactory.getLogger(BootloaderMsgTest::class.java)

    @After
    fun tearDown() {
        File(BootloaderMsg.miscFile).deleleIfExists()
    }

    @Test
    fun writeRebootBootloaderTest() {
        val msg = BootloaderMsg()
        msg.clearBootloaderMessage()
    }

    @Test
    fun readBootloaderMsgTest() {
        val msg = BootloaderMsg()
        msg.readBootloaderMsg()
        log.info(msg.toString())
    }

    @Test
    fun writeOptions() {
        val msg = BootloaderMsg()
        msg.updateBootloaderMessageInStruct(arrayOf(
                "--prompt_and_wipe_data",
                "--locale=zh_CN"))
        msg.writeBootloaderMessage()
    }


    @Test
    fun rebootRecovery() {
        val msg = BootloaderMsg()
        msg.updateBootloaderMessageInStruct(arrayOf())
        msg.writeBootloaderMessage()
    }

    @Test
    fun rebootCrash() {
        val msg = BootloaderMsg()
        msg.writeBootloaderMessage(arrayOf(
                "--prompt_and_wipe_data",
                "--reason=RescueParty",
                "--locale=en_US"))
    }

    @Test
    fun rebootOTA() {
        val msg = BootloaderMsg()
        msg.writeBootloaderMessage(arrayOf("--update_package=/cache/update.zip", "--security"))
    }

    @Test
    fun rebootWipeAb() {
        val msg = BootloaderMsg()
        msg.writeBootloaderMessage(arrayOf(
            "--wipe_ab",
            "--wipe_package_size=1024",
            "--locale=en_US"))
    }

    @Test
    fun rebootWipeData() {
        val msg = BootloaderMsg()
        msg.writeBootloaderMessage(arrayOf(
            "--wipe_data",
            "--reason=convert_fbe",
            "--locale=en_US"))
    }
}
