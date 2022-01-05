package init

class BootReason {
    /*
    Canonical boot reason format
        <reason>,<subreason>,<detail>…
     */
    class Reason private constructor(private val reason: String, subReason: String?, detail: String?) {
        companion object {
            val kernelSet = listOf("watchdog", "kernel_panic")
            val strongSet = listOf("recovery", "bootloader")
            val bluntSet = listOf("cold", "hard", "warm", "shutdown", "reboot")
            fun create(
                firstSpanReason: String,
                secondSpanReason: String? = null,
                detailReason: String? = null
            ): Reason {
                if (firstSpanReason !in mutableListOf<String>().apply {
                        addAll(kernelSet)
                        addAll(strongSet)
                        addAll(bluntSet)
                    }) {
                    throw IllegalArgumentException("$firstSpanReason is not allowd first span boot reason in Android")
                }
                return Reason(firstSpanReason, secondSpanReason, detailReason)
            }
        }//end-of-companion
    } //end-of-Reason
} //EOF
