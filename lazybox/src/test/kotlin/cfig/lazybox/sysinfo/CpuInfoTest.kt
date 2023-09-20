package cfig.lazybox.sysinfo

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.io.File

class CpuInfoTest {

    @Test
    fun probeInfo() {
        CpuInfo.construct()
    }
}