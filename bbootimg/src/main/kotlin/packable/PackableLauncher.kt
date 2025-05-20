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

package cfig.packable

import cfig.helper.Helper
import org.slf4j.LoggerFactory
import packable.DeviceTreeParser
import rom.sparse.SparseImgParser
import java.io.File
import java.util.Properties
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.system.exitProcess

class PackableLauncher

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(PackableLauncher::class.java)
    val devLog = LoggerFactory.getLogger("XXXX")
    val loadProperties: (String) -> Properties = { fileName ->
        Properties().apply {
            File(fileName).inputStream().use { load(it) }
        }
    }
    val packablePool = mutableMapOf<List<String>, KClass<IPackable>>()
    listOf(
        BootImgParser(),
        DeviceTreeParser(),
        DtboParser(),
        MiscImgParser(),
        OTAzipParser(),
        PayloadBinParser(),
        SparseImgParser(),
        VBMetaParser(),
        VendorBootParser(),
    ).forEach {
        @Suppress("UNCHECKED_CAST")
        packablePool.put(it.capabilities(), it::class as KClass<IPackable>)
    }
    packablePool.forEach {
        log.info("" + it.key + "/" + it.value)
    }
    var targetFile: String? = null
    var targetHandler: KClass<IPackable>? = null
    var targetDir: String? = null

    devLog.info("args: " + args.asList().toString())
    args.forEachIndexed { index, s ->
        devLog.info("  arg: #$index - $s")
    }

    run found@{
        for (currentLoopNo in 0..1) { //currently we have only 2 loops
            devLog.info("loop #" + currentLoopNo)
            if (args.size > 1) { // manual mode
                devLog.info("manual mode")
                targetFile = if (File(args[1]).isFile) { //unpack
                    File(args[1]).canonicalPath
                } else if (File(args[1]).isDirectory and File(args[1], "workspace.ini").isFile) { //pack
                    loadProperties(File(args[1], "workspace.ini").canonicalPath).getProperty("role")
                } else { //wrong
                    log.error("Not a file: " + args[1])
                    exitProcess(1)
                }
                devLog.warn("manual mode: file=$targetFile")
                targetDir = when (args.size) {
                    2 -> if (File(args[1]).isDirectory and File(args[1], "workspace.ini").isFile) { //arg = outDir
                        File(args[1]).canonicalPath
                    } else  {
                        Helper.prop("workDir") // arg  = outDir
                    }
                    3 -> File(args[2]).canonicalPath // arg = file
                    else -> {
                        throw IllegalArgumentException("too many args")
                    }
                }
                devLog.warn("manual mode: file=$targetFile, dir=$targetDir")
                packablePool
                    .filter { it.value.createInstance().loopNo == currentLoopNo }
                    .forEach { p ->
                        for (item in p.key) {
                            if (Pattern.compile(item).matcher(File(targetFile).name).matches()) {
                                log.info("Found: $targetFile, $item")
                                targetHandler = p.value
                                return@found
                            }
                        }
                    }
            } else { // lazy mode
                devLog.info("lazy mode (in current dir)")
                File(".").listFiles()!!.forEach { file ->
                    packablePool
                        .filter { it.value.createInstance().loopNo == currentLoopNo }
                        .forEach { p ->
                            for (item in p.key) {
                                if (Pattern.compile(item).matcher(file.name).matches()) {
                                    log.info("Found: " + file.name + ", " + item)
                                    targetFile = File(file.name).canonicalPath
                                    targetHandler = p.value
                                    return@found
                                }
                            }
                        }
                }//end-of-file-traversing
            }
        }//end-of-range-loop
    }//end-of-found@

    // /* 1 */ no-args & no-handler: help for IPackable
    // /* 2 */ no-args & handler   : help for Handler
    // /* 3 */ args    & no-handler: do nothing
    // /* 4 */ args    & handler   : work
    log.warn("args: ${args.size}, targetHandler: $targetHandler")
    when (listOf(args.isNotEmpty(), targetHandler != null)) {
        listOf(false, false) -> { /* 1 */
            log.info("help:")
            log.info("available IPackable subcommands are:")
            IPackable::class.declaredFunctions.forEach {
                log.info("\t" + it.name)
            }
            exitProcess(1)
        }

        listOf(false, true) -> {/* 2 */
            log.info("available ${targetHandler!!.simpleName} subcommands are:")
            targetHandler!!.declaredFunctions.forEach {
                log.info("\t" + it.name)
            }
            exitProcess(1)
        }

        listOf(true, false) -> {/* 3 */
            log.warn("No handler is activated, DO NOTHING!")
            exitProcess(2)
        }

        listOf(true, true) -> {/* 4 */
            log.info("continue ...")
        }
    }

    targetHandler?.let {
        log.warn("[$targetFile] will be handled by [${it.simpleName}]")
        val functions = it.declaredFunctions.filter { funcItem -> funcItem.name == args[0] }
        if (functions.size != 1) {
            log.error("command '${args[0]}' can not be recognized")
            log.info("available ${it.simpleName} subcommands are:")
            it.declaredFunctions.forEach { theFunc ->
                log.info("\t" + theFunc.name)
            }
            exitProcess(3)
        }
        log.warn("'${args[0]}' sequence initialized")

        log.warn("XXXX: args.size: ${args.size}")
        val c = (if (args.size == 1) { //lazy mode
            args.drop(1).toMutableList().apply {
                add(targetFile!!)
                //add(System.getProperty("user.dir"))
            }
        } else {
            args.drop(1)
        }).toTypedArray()
        val convertedArgs = c
        println("clazz = " + it.simpleName)
        println("func = " + functions[0])
        println("orig args:")
        args.forEachIndexed { index, s ->
            println("$index: $s")
        }
        println("Converted args:")
        convertedArgs.forEachIndexed { index, s ->
            println("$index: $s")
        }
        functions[0].call(it.createInstance(), *convertedArgs)
        log.warn("'${args[0]}' sequence completed")
    }
}
