package bcb

import cfig.bcb.VirtualABMsg
import cfig.bootimg.Common.Companion.deleleIfExists
import cfig.helper.Helper.Companion.check_call
import org.junit.After
import org.junit.Before

import org.junit.Ignore
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

class VirtualABMsgTest {
    private val log = LoggerFactory.getLogger(VirtualABMsgTest::class.java)

    @Before
    fun setUp() {
        "adb root".check_call()
        "adb wait-for-device".check_call()
        "adb shell dd if=/dev/block/by-name/misc of=/data/vendor/debug.misc skip=512 bs=64 count=1".check_call()
        "adb pull /data/vendor/debug.misc".check_call()
    }

    @Test
    @Ignore
    fun parseVAB() {
        val vab = VirtualABMsg(FileInputStream("debug.misc"))
        log.info("VAB msg: $vab")
    }

    @After
    fun tearDown() {
        File("debug.misc").deleleIfExists()
    }
}