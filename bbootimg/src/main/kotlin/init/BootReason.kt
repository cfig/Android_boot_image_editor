// Copyright 2022 yuyezhong@gmail.com
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
