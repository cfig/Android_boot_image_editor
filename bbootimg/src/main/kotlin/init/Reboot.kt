// Copyright 2019-2023 yuyezhong@gmail.com
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

package cfig.init

import rom.misc.MiscImage
import org.slf4j.LoggerFactory
import java.util.*

class Reboot {
    enum class RB_TYPE {
        ANDROID_RB_RESTART,
        ANDROID_RB_POWEROFF,
        ANDROID_RB_RESTART2,
        ANDROID_RB_THERMOFF
    }

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
                                val bcb: MiscImage.BootloaderMessage
                                if (bDynamicPartition == null || bDynamicPartition == false) {
                                    log.warn("$dynamicPartitionKey=false, using 'bootloader fastboot' instead of 'fastbootd'")
                                    rebootTarget = "bootloader"
                                    bcb = MiscImage.BootloaderMessage.rebootBootloader()
                                    log.info(bcb.toString())
                                } else {
                                    log.info("$dynamicPartitionKey=true, using fastbootd")
                                    bcb = MiscImage.BootloaderMessage.rebootFastboot2()
                                    rebootTarget = "recovery"
                                }
                                log.info(bcb.toString())
                            }
                            "bootloader" -> {
                                val bcb = MiscImage.BootloaderMessage.rebootBootloader()
                                log.info(bcb.toString())
                            }
                            "sideload", "sideload-auto-reboot" -> {
                                val bcb = MiscImage.BootloaderMessage().apply {
                                    updateBootloaderMessageInStruct(arrayOf("--" + rebootTarget.replace("-", "_")))
                                }
                                log.info(bcb.toString())
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
