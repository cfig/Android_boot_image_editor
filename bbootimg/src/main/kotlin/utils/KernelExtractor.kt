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

package cfig.utils

import cfig.helper.Helper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class KernelExtractor {
    val log: Logger = LoggerFactory.getLogger("KernelExtractor")

    fun envCheck(): Boolean {
        val envv = EnvironmentVerifier()
        return envv.hasLz4 && envv.hasXz && envv.hasGzip
    }

    fun run(fileName: String, workDir: File? = null): List<String> {
        val ret: MutableList<String> = mutableListOf()
        val kernelVersionFile = Helper.prop("kernelVersionFile")
        val kernelConfigFile = Helper.prop("kernelConfigFile")
        val cmdPrefix = if (EnvironmentVerifier().isWindows) "python " else ""
        val cmd = CommandLine.parse(cmdPrefix + Helper.prop("kernelExtracter")).let {
            it.addArgument("--input")
            it.addArgument(fileName)
            it.addArgument("--output-configs")
            it.addArgument(kernelConfigFile)
            it.addArgument("--output-version")
            it.addArgument(kernelVersionFile)
        }
        DefaultExecutor().let { it ->
            it.workingDirectory = workDir ?: File("../")
            try {
                it.execute(cmd)
                log.info(cmd.toString())
                val kernelVersion = File(kernelVersionFile).readLines()
                log.info("kernel version: $kernelVersion")
                log.info("kernel config dumped to : $kernelConfigFile")
                ret.add(kernelVersion.toString())
                ret.add(kernelVersionFile)
                ret.add(kernelConfigFile)
            } catch (e: org.apache.commons.exec.ExecuteException) {
                listOf(kernelConfigFile, kernelVersionFile).forEach { fn ->
                    File(fn).let { f ->
                        if (f.exists()) f.delete()
                    }
                }
                log.warn("can not parse kernel info")
            }
        }
        return ret
    }
}
