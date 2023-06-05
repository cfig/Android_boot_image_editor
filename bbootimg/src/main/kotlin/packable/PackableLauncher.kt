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

import cfig.utils.SparseImgParser
import org.slf4j.LoggerFactory
import packable.DeviceTreeParser
import java.io.File
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.system.exitProcess

class PackableLauncher

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger(PackableLauncher::class.java)
    val packablePool = mutableMapOf<List<String>, KClass<IPackable>>()
    listOf(
        DtboParser(), VBMetaParser(), BootImgParser(), SparseImgParser(), VendorBootParser(), PayloadBinParser(),
        MiscImgParser(),
        DeviceTreeParser()
    ).forEach {
        @Suppress("UNCHECKED_CAST")
        packablePool.put(it.capabilities(), it::class as KClass<IPackable>)
    }
    packablePool.forEach {
        log.debug("" + it.key + "/" + it.value)
    }
    var targetFile: String? = null
    var targetHandler: KClass<IPackable>? = null
    run found@{
        for (currentLoopNo in 0..1) { //currently we have only 2 loops
            File(".").listFiles()!!.forEach { file ->
                packablePool
                    .filter { it.value.createInstance().loopNo == currentLoopNo }
                    .forEach { p ->
                        for (item in p.key) {
                            if (Pattern.compile(item).matcher(file.name).matches()) {
                                log.debug("Found: " + file.name + ", " + item)
                                targetFile = file.name
                                targetHandler = p.value
                                return@found
                            }
                        }
                    }
            }//end-of-file-traversing
        }//end-of-range-loop
    }//end-of-found@

    // /* 1 */ no-args & no-handler: help for IPackable
    // /* 2 */ no-args & handler   : help for Handler
    // /* 3 */ args    & no-handler: do nothing
    // /* 4 */ args    & handler   : work
    when (listOf(args.isEmpty(), targetHandler == null)) {
        listOf(true, true) -> { /* 1 */
            log.info("help:")
            log.info("available IPackable subcommands are:")
            IPackable::class.declaredFunctions.forEach {
                log.info("\t" + it.name)
            }
            exitProcess(1)
        }
        listOf(true, false) -> {/* 2 */
            log.info("available ${targetHandler!!.simpleName} subcommands are:")
            targetHandler!!.declaredFunctions.forEach {
                log.info("\t" + it.name)
            }
            exitProcess(1)
        }
        listOf(false, true) -> {/* 3 */
            log.warn("No handler is activated, DO NOTHING!")
            exitProcess(2)
        }
        listOf(false, false) -> {/* 4 */
            log.debug("continue ...")
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
        val reflectRet = when (functions[0].parameters.size) {
            1 -> {
                functions[0].call(it.createInstance())
            }
            2 -> {
                functions[0].call(it.createInstance(), targetFile!!)
            }
            3 -> {
                if (args.size != 2) {
                    log.info("invoke: ${it.qualifiedName}, $targetFile, " + targetFile!!.removeSuffix(".img"))
                    functions[0].call(it.createInstance(), targetFile!!, targetFile!!.removeSuffix(".img"))
                } else {
                    log.info("invoke: ${it.qualifiedName}, $targetFile, " + args[1])
                    functions[0].call(it.createInstance(), targetFile!!, args[1])
                }
            }
            else -> {
                functions[0].parameters.forEach { kp ->
                    println("Param: " + kp.index + " " + kp.type + " " + kp.name)
                }
                log.error("I am confused by so many parameters")
                exitProcess(4)
            }
        }
        if (functions[0].returnType.toString() != Unit.toString()) {
            log.info("ret: $reflectRet")
        }
        log.warn("'${args[0]}' sequence completed")
    }
}
