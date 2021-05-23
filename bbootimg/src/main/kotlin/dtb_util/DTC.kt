// Copyright 2021 yuyezhong@gmail.com
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

package cfig.dtb_util

import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory

class DTC {
    private val log = LoggerFactory.getLogger(DTC::class.java)

    fun decompile(dtbFile: String, outFile: String): Boolean {
        log.info("parsing DTB: $dtbFile")
        val cmd = CommandLine.parse("dtc -I dtb -O dts").let {
            it.addArguments("$dtbFile")
            it.addArguments("-o $outFile")
        }

        CommandLine.parse("fdtdump").let {
            it.addArguments("$dtbFile")
        }

        DefaultExecutor().let {
            try {
                it.execute(cmd)
                log.info(cmd.toString())
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not parse DTB: $dtbFile")
                return false
            }
        }
        return true
    }

    fun compile(dtsFile: String, outFile: String): Boolean {
        log.info("compiling DTS: $dtsFile")
        val cmd = CommandLine.parse("dtc -I dts -O dtb").let {
            it.addArguments("$dtsFile")
            it.addArguments("-o $outFile")
        }

        DefaultExecutor().let {
            try {
                it.execute(cmd)
                log.info(cmd.toString())
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not compile DTB: $dtsFile")
                return false
            }
        }
        return true
    }
}
