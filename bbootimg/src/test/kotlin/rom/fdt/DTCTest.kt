package rom.fdt

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

class DTCTest {
    private lateinit var tempFile :java.nio.file.Path

    @Before
    fun before() {
        tempFile = createTempFile()
        val dtbData = this::class.java.classLoader.getResourceAsStream("multiple_DT.dtb")!!.readAllBytes()
        tempFile.writeBytes(dtbData)
    }

    @After
    fun after() {
        tempFile.deleteIfExists()
    }

    @Test
    fun doIt() {
        val dtbList = DTC.parseMultiple(tempFile.toAbsolutePath().toString())
        check(dtbList.size == 4)
    }
}
