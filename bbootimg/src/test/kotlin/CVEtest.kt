import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.Callable
import java.util.regex.Matcher
import java.util.regex.Pattern

class CVEtest {
    private val printkFormat = """
0xffffff8008cce7ee : "Rescheduling interrupts"
0xffffff8008cce806 : "Function call interrupts"
0xffffff8008cce81f : "CPU stop interrupts"
0xffffff8008cce833 : "CPU stop (for crash dump) interrupts"
0xffffff8008cce858 : "Timer broadcast interrupts"
0xffffff8008cce873 : "IRQ work interrupts"
0xffffff8008cce887 : "CPU wake-up interrupts"
0xffffff8009070140 : "rcu_sched"
0xffffff8009070500 : "rcu_bh"
0xffffff8009070920 : "rcu_preempt"
"""

    private val printkFormatPatch = """
0x0 : "Rescheduling interrupts"
0x0 : "Function call interrupts"
0x0 : "CPU stop interrupts"
0x0 : "Timer broadcast interrupts"
0x0 : "IRQ work interrupts"
0x0 : "CPU wake-up interrupts"
0x0 : "CPU backtrace"
0x0 : "rcu_bh"
0x0 : "rcu_preempt"
0x0 : "rcu_sched"
""".trimIndent()

    @Test
    fun testPocCVE_2017_0630() {
        val printkFormats: String = printkFormatPatch
        val pointerStrings = printkFormats.split("\n").toTypedArray()
        assertNotKernelPointer(object : Callable<String?> {
            var index = 0
            override fun call(): String? {
                while (index < pointerStrings.size) {
                    val line = pointerStrings[index]
                    val pattern = "0x"
                    val startIndex = line.indexOf(pattern)
                    if (startIndex == -1) {
                        index++
                        continue
                    }
                    return line.substring(startIndex + pattern.length)
                }
                return null
            }
        }, null)
    }

    fun assertNotKernelPointer(getPtrFunction: Callable<String?>, deviceToReboot: String?) {
        var ptr: String? = null
        for (i in 0..3) { // ~0.4% chance of false positive
            ptr = getPtrFunction.call()
            if (ptr == null) {
                return
            }
            if (!isKptr(ptr)) {
                // quit early because the ptr is likely hashed or zeroed.
                return
            }
        }
        throw IllegalArgumentException("\"$ptr\" is an exposed kernel pointer.")
    }

    private fun isKptr(ptr: String): Boolean {
        val RADIX_HEX = 16
        val m: Matcher = Pattern.compile("[0-9a-fA-F]*").matcher(ptr)
        if (!m.find() || m.start() != 0) {
            // ptr string is malformed
            return false
        }
        val length: Int = m.end()
        if (length == 8) {
            // 32-bit pointer
            val address = BigInteger(ptr.substring(0, length), RADIX_HEX)
            // 32-bit kernel memory range: 0xC0000000 -> 0xffffffff
            // 0x3fffffff bytes = 1GB /  0xffffffff = 4 GB
            // 1 in 4 collision for hashed pointers
            return address >= BigInteger("C0000000", RADIX_HEX)
        } else if (length == 16) {
            // 64-bit pointer
            val address = BigInteger(ptr.substring(0, length), RADIX_HEX)
            // 64-bit kernel memory range: 0x8000000000000000 -> 0xffffffffffffffff
            // 48-bit implementation: 0xffff800000000000; 1 in 131,072 collision
            // 56-bit implementation: 0xff80000000000000; 1 in 512 collision
            // 64-bit implementation: 0x8000000000000000; 1 in 2 collision
            return address >= BigInteger("ff80000000000000", RADIX_HEX)
        }
        return false
    }
}