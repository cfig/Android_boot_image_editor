import cfig.EnvironmentVerifier
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before

class EnvironmentVerifierTest {
    private val envv = EnvironmentVerifier()

    @Test
    fun getHasLz4() {
        val hasLz4 = envv.hasLz4
        println("hasLz4 = $hasLz4")
    }

    @Test
    fun getHasDtc() {
        val hasDtc = envv.hasDtc
        println("hasDtc = $hasDtc")

    }

    @Test
    fun getHasXz() {
        val hasXz = envv.hasXz
        println("hasXz = $hasXz")
    }

    @Test
    fun getGzip() {
        val h = envv.hasGzip
        println("hasGzip = $h")
    }
}
