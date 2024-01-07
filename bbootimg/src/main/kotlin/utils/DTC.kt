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

import cc.cfig.io.Struct
import cfig.helper.Dumpling
import cfig.helper.Helper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DTC {
    private val log = LoggerFactory.getLogger(DTC::class.java)

    data class DtbEntry(
        var seqNo: Int = 0,
        var offset: Long = 0,
        var header: FdtHeader = FdtHeader(),
    )

    data class FdtHeader(
        var magic: Int = 0,
        val totalsize: Int = 0,
        val offDtStruct: Int = 0,
        val offDtStrings: Int = 0,
        val offMemRsvmap: Int = 0,
        val version: Int = 0,
        val lastCompVersion: Int = 0,
        val bootCpuidPhys: Int = 0,
        val sizeDtStrings: Int = 0,
        val sizeDtStruct: Int = 0
    ) {
        companion object {
            private const val MAGIC = 0xd00dfeedu
            const val FORMAT_STRING = ">10i"
            const val SIZE = 40

            init {
                check(Struct(FORMAT_STRING).calcSize() == SIZE)
            }

            @Throws(IllegalStateException::class)
            fun parse(iS: InputStream): FdtHeader {
                val info = Struct(FORMAT_STRING).unpack(iS)
                val ret = FdtHeader(
                    info[0] as Int,
                    info[1] as Int,
                    info[2] as Int,
                    info[3] as Int,
                    info[4] as Int,
                    info[5] as Int,
                    info[6] as Int,
                    info[7] as Int,
                    info[8] as Int,
                    info[9] as Int
                )
                check(ret.magic.toUInt() == MAGIC) { "bad magic: ${ret.magic}" }
                return ret
            }
        }
    }

    fun decompile(dtbFile: String, outFile: String): Boolean {
        log.info("parsing DTB: $dtbFile")
        //CommandLine.parse("fdtdump").let {
        //    it.addArguments("$dtbFile")
        //}
        //dtb-> dts
        DefaultExecutor().let {
            try {
                val cmd = CommandLine.parse("dtc -q -I dtb -O dts").apply {
                    addArguments(dtbFile)
                    addArguments("-o $outFile")
                }
                it.execute(cmd)
                log.info(cmd.toString())
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not parse DTB: $dtbFile")
                return false
            }
        }
        //dts -> yaml
        DefaultExecutor().let {
            try {
                val cmd = CommandLine.parse("dtc -q -I dts -O yaml").apply {
                    addArguments(outFile)
                    addArguments("-o $outFile.yaml")
                }
                it.execute(cmd)
                log.info(cmd.toString())
            } catch (e: org.apache.commons.exec.ExecuteException) {
                log.error("can not transform DTS: $outFile")
                return false
            }
        }
        return true
    }

    fun compile(dtsFile: String, outFile: String): Boolean {
        log.info("compiling DTS: $dtsFile")
        val cmd = CommandLine.parse("dtc -q -I dts -O dtb").let {
            it.addArguments(dtsFile)
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

    companion object {
        private val log = LoggerFactory.getLogger(DTC::class.java)
        fun parseMultiple(fileName: String): MutableList<DtbEntry> {
            val ret = mutableListOf<DtbEntry>()
            var seqNo = 0
            while (true) {
                try {
                    val index = ret.sumOf { it.header.totalsize.toLong() }
                    val data = Dumpling(fileName).readFully(Pair(index, FdtHeader.SIZE))
                    val header = FdtHeader.parse(data.inputStream())
                    log.info("Found FDT header: #${seqNo} $header")
                    ret.add(DtbEntry(seqNo, index, header))
                    seqNo++
                } catch (e: IllegalStateException) {
                    log.info("no more FDT header")
                    break
                }
            }
            val remainder = File(fileName).length() - ret.sumOf { it.header.totalsize }.toLong()
            if (remainder == 0L) {
                log.info("Successfully parsed ${ret.size} FDT headers")
            } else {
                log.warn("Successfully parsed ${ret.size} FDT headers, remainder: $remainder bytes")
            }
            return ret
        }

        fun extractMultiple(fileStem: String, entries: List<DtbEntry>) {
            entries.forEach {
                val slice = Helper.Slice(
                    fileStem,
                    it.offset.toInt(), it.header.totalsize, "${fileStem}.${it.seqNo}"
                )
                Helper.extractFile(slice.srcFile, slice.dumpFile, slice.offset.toLong(), slice.length)
                if (EnvironmentVerifier().hasDtc) {
                    DTC().decompile(slice.dumpFile, slice.dumpFile + "." + Helper.prop("config.dts_suffix"))
                }
            }
        }

        fun packMultiple(fileStem: String, entries: List<DtbEntry>) {
            if (EnvironmentVerifier().hasDtc) {
                entries.forEach {
                    DTC().compile(
                        fileStem + ".${it.seqNo}." + Helper.prop("config.dts_suffix"),
                        fileStem + "." + it.seqNo
                    )
                }
                FileOutputStream(fileStem).use { outFile ->
                    entries.indices.forEach {
                        log.info("Appending ${fileStem}.${it} ...")
                        outFile.write(File("$fileStem.$it").readBytes())
                    }
                    log.info("Appended ${entries.size} DTBs to ${fileStem}")
                }
            }
        }
    }
}
