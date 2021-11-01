package bcb

import cfig.bcb.VirtualABMsg
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.helper.Helper
import cfig.helper.Helper.Companion.check_call
import org.apache.commons.exec.CommandLine
import org.junit.*

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class VirtualABMsgTest {
    private val log = LoggerFactory.getLogger(VirtualABMsgTest::class.java)

    @Before
    fun setUp() {
        Assume.assumeTrue(
            try {
                "adb --version".check_call()
                true
            } catch (e: IOException) {
                false
            }
        )
        Assume.assumeTrue(Helper.powerRun3(CommandLine.parse("adb root"), null)[0] as Boolean)
        "adb wait-for-device".check_call()
        "adb shell dd if=/dev/block/by-name/misc of=/data/vendor/debug.misc skip=512 bs=64 count=1".check_call()
        "adb pull /data/vendor/debug.misc".check_call()
    }

    @Test
    fun parseVAB() {
        FileInputStream("debug.misc").use {
            val vab = VirtualABMsg(it)
            log.info("VAB msg: $vab")
        }
    }

    @After
    fun tearDown() {
        File("debug.misc").deleleIfExists()
    }
}