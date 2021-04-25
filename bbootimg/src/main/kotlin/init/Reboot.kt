package cfig.init

import cfig.bootloader_message.BootloaderMsg
import org.slf4j.LoggerFactory
import java.util.*

class Reboot {
    enum class RB_TYPE {
        ANDROID_RB_RESTART,
        ANDROID_RB_POWEROFF,
        ANDROID_RB_RESTART2,
        ANDROID_RB_THERMOFF
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    companion object {
        private val log = LoggerFactory.getLogger(Reboot::class.java)
        const val dynamicPartitionKey = "ro.boot.dynamic_partitions"
        const val lastRebootReasonKey = "persist.sys.boot.reason"

        private fun doReboot(cmd: RB_TYPE, reason: String, rebootTarget: String) {
            log.info("DoReboot: cmd=$cmd, reason=$reason, tgt=$rebootTarget")
            val reasons = reason.split(",").toTypedArray()
            val props = Properties()
            props.setProperty(lastRebootReasonKey, reason)
            if (reasons.size > 1 && reasons[0] == "reboot") {
                if (reasons[1] in setOf("recovery", "bootloader", "cold", "hard", "warn")) {
                    props.setProperty(lastRebootReasonKey, reason.substring(7))
                } else {
                    //pass
                }
            }
        }

        private fun doReboot(cmd: String, rebootTarget: String) {
            log.info("cmd=[$cmd], rebootTarget=[$rebootTarget]")
        }

        // setprop sys.powerctl <value>
        fun handlePowerctlMessage(inValue: String, props: Properties? = null) {
            log.info("handlePowerctlMessage($inValue)")
            val args = inValue.split(",").toTypedArray()
            var cmd: String
            var rebootTarget = ""
            if (args.size > 4) {
                throw java.lang.IllegalArgumentException("powerctl: unrecognized command $args")
            }
            when (args[0]) {
                "shutdown" -> {
                    cmd = "ANDROID_RB_POWEROFF"
                    if (args.size == 2) {
                        when (args[1]) {
                            "userrequested" -> {
                                log.info("need to run_fsck")
                            }
                            "thermal" -> {
                                cmd = "ANDROID_RB_THERMOFF"
                                log.info("TurnOffBacklight()")
                            }
                            else -> {
                                //pass
                            }
                        }
                    } else {
                        //pass
                    }
                }
                "reboot" -> {
                    cmd = "ANDROID_RB_RESTART2"
                    if (args.size >= 2) {
                        rebootTarget = args[1]
                        val bDynamicPartition: Boolean? = props?.let { propsNotNull ->
                            propsNotNull.get(dynamicPartitionKey)?.let { if (it == "true") true else null }
                        }
                        when (rebootTarget) {
                            "fastboot" -> {
                                if (bDynamicPartition == null || bDynamicPartition == false) {
                                    log.warn("$dynamicPartitionKey=false, using 'bootloader fastboot' instead of 'fastbootd'")
                                    rebootTarget = "bootloader"
                                    BootloaderMsg().writeRebootBootloader()
                                } else {
                                    log.info("$dynamicPartitionKey=true, using fastbootd")
                                    BootloaderMsg().writeBootloaderMessage(arrayOf("--fastboot"))
                                    rebootTarget = "recovery"
                                }
                            }
                            "bootloader" -> {
                                BootloaderMsg().writeRebootBootloader()
                            }
                            "sideload", "sideload-auto-reboot" -> {
                                BootloaderMsg().writeBootloaderMessage(
                                    arrayOf("--" + rebootTarget.replace("-", "_"))
                                )
                                rebootTarget = "recovery"
                            }
                            else -> {
                            }
                        }//end-of-when-rebootTarget

                        for (i in 2 until args.size) {
                            log.info("rebootTarget: append " + args[i])
                            rebootTarget += ("," + args[i])
                        }
                    }//end-of-cmd
                }//end-of-cmd=reboot
                else -> {//not shutdown/reboot
                    throw java.lang.IllegalArgumentException("powerctl: unrecognized command $args")
                }
            }//end-of-args[0]

            log.debug("ActionManager::GetInstance().ClearQueue()")
            log.debug("ActionManager::GetInstance().QueueEventTrigger(\"shutdown\")")
            log.debug("ActionManager::GetInstance().QueueBuiltinAction(shutdown_handler, \"shutdown_done\")")
            doReboot(cmd, rebootTarget)
        }//end-of-handlePowerctlMessage
    }//end-of-companion
}
