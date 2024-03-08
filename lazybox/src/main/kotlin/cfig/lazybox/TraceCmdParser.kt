package cfig.lazybox

import com.github.freva.asciitable.AsciiTable
import com.github.freva.asciitable.Column
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class TraceCmdParser {
    companion object {
        val log = LoggerFactory.getLogger(TraceCmdParser::class.qualifiedName)
    }
    data class Event(
        val task: String,
        val type: String,
        val cpu: Int,
        val timestamp: Double,
        val target: Int,
        val step: Int,
        val info: String,
    )

    fun readEventsFromFile(filePath: String): List<Event> {
        val eventList = mutableListOf<Event>()
        File(filePath).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (!line.startsWith(" ")) {
                    log.info("Skip line: $line")
                    return@forEach
                }
                val parts = line.trim().split("\\s+".toRegex())
                val task = parts[0].trim()
                val cpu = parts[1].trim('[', ']').toInt()
                val timestampString = parts[2].split(':')[0]
                val timestamp = timestampString.toDouble()
                val eventType = parts[3].removeSuffix(":")
                var info = line.trim().substring(line.trim().indexOf(parts[4]))
                log.debug(info)
                info.split(" ").forEachIndexed { index, s ->
                    log.debug("info#$index: $s")
                }
                var target = 0
                var step = 0
                var infoCpu = ""
                var infoState = ""
                var infoRet = ""
                val infoList = mutableListOf<String>()
                var bWaitForTarget = false
                var bWaitForStep = false
                var bWaitForCPU = false
                var bWaitForState = false
                var bWaitForRet = false
                for (item in info.split("\\s+".toRegex())) {
                    when (item) {
                        "target:" -> {
                            bWaitForTarget = true
                            continue
                        }

                        "step:" -> {
                            bWaitForStep = true
                            continue
                        }

                        "cpu:" -> {
                            bWaitForCPU = true
                            continue
                        }

                        "state:" -> {
                            bWaitForState = true
                            continue
                        }

                        "ret:" -> {
                            bWaitForRet = true
                            continue
                        }

                        else -> {
                            if (bWaitForTarget) {
                                target = item.toInt()
                                bWaitForTarget = false
                                continue
                            }
                            if (bWaitForStep) {
                                step = item.toInt()
                                bWaitForStep = false
                                continue
                            }
                            if (bWaitForCPU) {
                                bWaitForCPU = false
                                infoCpu = item
                                continue
                            }
                            if (bWaitForState) {
                                bWaitForState = false
                                infoState = item
                                continue
                            }
                            if (bWaitForRet) {
                                bWaitForRet = false
                                infoRet = item
                                continue
                            }
                            if (item.startsWith("(")) {
                                //info = "cpu=$infoCpu $item"
                                continue
                            }
                            if (item.startsWith("cpu_id=")) {
                                infoCpu = item.substringAfter("cpu_id=")
                                continue
                            }
                            if (item.startsWith("state=")) {
                                infoState = item.substringAfter("state=")
                                continue
                            }
                            infoList.add(item)
                        }
                    }
                }
                eventList.add(Event(task, eventType, cpu, timestamp, target, step, info))
            }
        }
        return eventList
    }

    private fun calcTimeDiff(eventList: List<Event>): List<Map<String, Any>> {
        val result = mutableListOf<MutableMap<String, Any>>()
        eventList.sortedBy { it.timestamp }.forEach { evt ->
            result.add(
                mutableMapOf(
                    "task" to evt.task,
                    "eventType" to evt.type,
                    "cpu" to evt.cpu,
                    "startTime" to (if (!evt.type.endsWith("_exit")) evt.timestamp else 0.0),
                    "endTime" to (if (evt.type.endsWith("_exit")) evt.timestamp else 0.0),
                    "timeDifference" to "",
                    "target" to evt.target,
                    "step" to evt.step,
                    "info" to evt.info,
                )
            )
        }

        eventList.map { it.step }.toSet().forEach { stepNo ->
            val eventPair = eventList.filter { it.step == stepNo }.sortedBy { it.timestamp }
            if (eventPair.size.rem(2) == 0) {
                eventPair.windowed(2, step = 2).forEachIndexed { index, events ->
                    val startEvent = events[0]
                    val endEvent = events[1]
                    if (startEvent.type.endsWith("_enter") and endEvent.type.endsWith("_exit")) {
                        val timeDifference = (endEvent.timestamp - startEvent.timestamp) * 1000
                        var timeDiffStr = "%.2f".format(timeDifference)
                        if (timeDiffStr == "0.00") {
                            timeDiffStr = "0"
                        }
                        val idx = result.indexOfFirst { it["step"] == stepNo && it["startTime"] == startEvent.timestamp }
                        result.get(idx).put("timeDifference", timeDiffStr)
                        val idx2 = result.indexOfFirst { it["step"] == stepNo && it["endTime"] == endEvent.timestamp }
                        result.get(idx2).put("timeDifference", "-")
                    } else {
                        println("Invalid event pair: $startEvent, $endEvent")
                        return@forEachIndexed
                    }
                }
            }
        }

        return result
    }

    fun mainFunction(filePath: String) {
        val eventList = readEventsFromFile(filePath)
        val timeTable = calcTimeDiff(eventList)
        val tb = AsciiTable.getTable(
            timeTable, Arrays.asList(
                Column().header("Task").with { it["task"] as String },
                Column().header("Event Type").with { it["eventType"] as String },
                Column().header("Step").with { (it["step"] as Int).toString() },
                Column().header("CPU").with { (it["cpu"] as Int).toString() },
                Column().header("Start Time (s)")
                    .with { val v = (it["startTime"] as Double).toString(); if (v == "0.0") "" else v },
                Column().header("End Time (s)")
                    .with { val v = (it["endTime"] as Double).toString(); if (v == "0.0") "" else v },
                Column().header("Duration (ms)").with { it["timeDifference"] as String },
                Column().header("Info").with { "`" + it["info"] as String + "`" },
            )
        )
        val mdFile = File(File(filePath).parent, "trace.md")
        log.info("Writing to $mdFile ...")
        mdFile.let { outFile ->
            val cssStyle: String = """
    |<style>
    |  table {
    |    border-collapse: collapse;
    |  }
    |
    |  table, th, td {
    |    border: 1px solid black;
    |  }
    |</style>
""".trimMargin()
            val sep = "| -- | -- | -- | -- | -- | -- | -- | -- |"
            val tableLines = tb.toString().split("\n").filterNot { it.contains("+--") }
            val modifiedList = mutableListOf<String>().apply {
                addAll(tableLines.subList(0, 1)) // Add items 0 (inclusive) to 1 (exclusive)
                add(sep) // Add the new string
                addAll(tableLines.subList(1, tableLines.size)) // Add items 1 (inclusive) to the end
            }
            outFile.writeText(cssStyle)
            outFile.appendText("\n::: {style=\"text-align:center\"}\n")
            outFile.appendText("# Trace\n")
            outFile.appendText(":::\n")
            outFile.appendText("Generated at ${getCurrentTime()}\n\n")
            modifiedList.forEach { outFile.appendText("$it\n") }
        }

        val htmlFile = File(File(filePath).parent, "trace.html")
        runPandocCommand(mdFile.toString(), htmlFile.toString())
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentTime = Date()
        return dateFormat.format(currentTime)
    }

    private fun runPandocCommand(inputFile: String, outputFile: String) {
        val pandocCommand = "pandoc $inputFile -o $outputFile"
        log.info("Pandoc: $inputFile -> $outputFile")
        try {
            val processBuilder = ProcessBuilder("/bin/bash", "-c", pandocCommand)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                log.info(line)
            }
            val exitCode = process.waitFor()
            log.info("Pandoc process exited with code $exitCode")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
