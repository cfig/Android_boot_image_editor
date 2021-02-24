package cfig.helper

import cfig.helper.ZipHelper.Companion.dumpEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ZipHelperTest {

    @Before
    fun setUp() {
        File("out").let {
            if (!it.exists()) it.mkdir()
        }
    }

    @After
    fun tearDown() {
        File("out").deleteRecursively()
    }

    @Test
    fun unzip() {
        val zf = ZipHelperTest::class.java.classLoader.getResource("appcompat.zip").file
        ZipHelper.unzip(File(zf).path, "out")
        File("out").deleteRecursively()
    }

    @Test
    fun dumpEntry() {
        val zf = ZipHelperTest::class.java.classLoader.getResource("appcompat.zip").file
        ZipFile(zf).use {
           it.dumpEntry("webview.log", File("out/webview.log"))
        }
    }
}