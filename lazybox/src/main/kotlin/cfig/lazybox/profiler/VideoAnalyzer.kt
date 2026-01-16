package cfig.lazybox.profiler

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_imgcodecs.*
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
import org.bytedeco.javacv.FFmpegLogCallback
import org.slf4j.LoggerFactory
import java.util.Properties

class VideoAnalyzer {
    // === 配置区 ===
    val RES_DIR = "res"
    val DESC_FILE = "desc.txt"
    val LOG_PATTERN_FILE = "res/log_patterns.txt"

    // === 数据类定义 ===
    data class StageVariation(
        val file: String,
        val tpl: Mat,
        val threshold: Double = 0.8,
        val minBrightness: Int = 0,
        val isBrightnessCheck: Boolean = false,
        val maxBrightness: Int = 255
    )

    data class LogicalStage(
        val name: String,
        val isOptional: Boolean,
        val variations: MutableList<StageVariation> = mutableListOf(),
        var foundAtTs: Double? = null,
        var foundAtFrame: Int? = null
    )

    // Helper for parsing config
    private data class ParsedStageInfo(
        val stageName: String,
        val imgFile: String,
        val isOptional: Boolean,
        val threshold: Double = 0.8,
        val minBrightness: Int = 0,
        val isBrightnessCheck: Boolean = false,
        val maxBrightness: Int = 255
    )

    // Helper data class for the merged table
    private data class MergedTimelineEvent(
        val timestamp: String,
        val source: String,
        val eventName: String,
        var deltaA: String = "-",
        var deltaB: String = "-",
        val frame: String = "-"
    )

    class BootProfiler(private val resDir: String, private val descFilename: String) {
        var stages: List<LogicalStage> = listOf()
        var frameTimeMap: Map<Int, Double> = mapOf()
        private val launcherStartName = "Launcher start"
        private val launcherLoadedName = "Launcher loaded"
        private val launcherTopRatio = 0.18
        private val launcherLowerRatio = 0.5
        private val launcherSimMargin = 0.05

        init {
            stages = loadConfig(File(resDir, descFilename))
        }

        // 1. 加载配置 (复刻 Python _load_config)
        private fun loadConfig(configFile: File): List<LogicalStage> {
            if (!configFile.exists()) {
                log.error("[Error] Description file not found: ${configFile.absolutePath}")
                exitProcess(1)
            }

            log.info("Loading config file: ${configFile.absolutePath}")

            // 临时存储解析结果
            val parsedStages = mutableListOf<ParsedStageInfo>()

            try {
                configFile.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("|").map { it.trim() }
                        if (parts.size >= 2) {
                            val imgName = parts[0]
                            var desc = parts[1]
                            val isOptional = desc.endsWith("?")
                            if (isOptional) {
                                desc = desc.dropLast(1).trim()
                            }

                            // 解析可选的参数
                            var threshold = 0.8
                            var minBrightness = 0
                            var isBrightnessCheck = false
                            var maxBrightness = 255
                            for (i in 2 until parts.size) {
                                val param = parts[i]
                                if (param.startsWith("threshold=")) {
                                    threshold = param.substringAfter("=").toDoubleOrNull() ?: 0.8
                                } else if (param.startsWith("min_brightness=")) {
                                    minBrightness = param.substringAfter("=").toIntOrNull() ?: 0
                                } else if (param.startsWith("is_brightness_check=")) {
                                    isBrightnessCheck = param.substringAfter("=").equals("true", ignoreCase = true)
                                } else if (param.startsWith("max_brightness=")) {
                                    maxBrightness = param.substringAfter("=").toIntOrNull() ?: 255
                                }
                            }

                            parsedStages.add(ParsedStageInfo(desc, imgName, isOptional, threshold, minBrightness, isBrightnessCheck, maxBrightness))
                        } else {
                            log.warn("[Warning] Skipping malformed line: $line")
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("[Error] Failed to read config: ${e.message}")
                exitProcess(1)
            }

            // 按名称分组 (Group by Name)
            val groupedMap = LinkedHashMap<String, LogicalStage>()

            for (parsed in parsedStages) {
                val imgPath = File(resDir, parsed.imgFile)
                if (!imgPath.exists()) {
                    log.error("[Error] Image file not found: ${imgPath.absolutePath}")
                    exitProcess(1)
                }

                // 读取图片 (灰度) - 如果是亮度检查，可以跳过读取图片
                val tpl = if (!parsed.isBrightnessCheck) {
                    val mat = imread(imgPath.absolutePath, IMREAD_GRAYSCALE)
                    if (mat == null || mat.empty()) {
                        log.error("[Error] Failed to read image: ${imgPath.absolutePath}")
                        exitProcess(1)
                    }
                    mat
                } else {
                    Mat()  // 亮度检查不需要模板
                }

                val stage = groupedMap.getOrPut(parsed.stageName) {
                    LogicalStage(parsed.stageName, parsed.isOptional)
                }
                stage.variations.add(StageVariation(parsed.imgFile, tpl, parsed.threshold, parsed.minBrightness, parsed.isBrightnessCheck, parsed.maxBrightness))
            }

            val finalStages = groupedMap.values.toList()
            finalStages.forEach { stage ->
                val files = stage.variations.joinToString(", ") { it.file }
                val optStr = if (stage.isOptional) "(Optional)" else ""
                log.info("  + Loaded Logical Stage: [${stage.name}] -> [$files] $optStr")
            }

            return finalStages
        }

        // 2. 加载 CSV (纯 Kotlin 实现，无额外依赖)
        fun loadCsvTimestamps(csvPath: File) {
            val map = HashMap<Int, Double>()
            try {
                var isHeader = true
                csvPath.forEachLine { line ->
                    if (isHeader) {
                        // 简单的 Header 检查，假设第一行是 Header
                        // 如果第一行也是数据，可以去掉这个 check，或者根据内容判断
                        if (line.contains("Frame") || line.contains("Timestamp")) {
                            isHeader = false
                            return@forEachLine
                        }
                        isHeader = false
                    }

                    if (line.isNotBlank()) {
                        val parts = line.split(",")
                        if (parts.size >= 2) {
                            try {
                                // 假设格式: Frame, Timestamp (可能还有其他列)
                                val frameId = parts[0].trim().toInt()
                                val timestamp = parts[1].trim().toDouble()
                                map[frameId] = timestamp
                            } catch (e: NumberFormatException) {
                                // 忽略解析错误的行
                            }
                        }
                    }
                }
                this.frameTimeMap = map
                log.info("CSV data loaded: ${map.size} frames")
            } catch (e: Exception) {
                log.error("[Error] Failed to load CSV: ${e.message}")
                exitProcess(1)
            }
        }

        // 3. 运行分析
        fun runAnalysis(videoPath: File, reportPath: File) {
            if (stages.isEmpty()) {
                log.error("No stages defined, exiting.")
                return
            }

            // 抑制 FFmpeg 啰嗦的日志
            FFmpegLogCallback.set()

            val grabber = FFmpegFrameGrabber(videoPath)
            try {
                grabber.start()
            } catch (e: Exception) {
                log.error("Failed to open video: ${videoPath.absolutePath}")
                return
            }

            val converter = OpenCVFrameConverter.ToMat()
            val totalFrames = grabber.lengthInVideoFrames
            var currentTargetIdx = 0
            val launcherMatcher = buildLauncherMatcher()

            log.info("Analyzing video ($totalFrames frames)...")

            // 循环遍历视频
            // 注意：grabber.frameNumber 是当前由 grabber 准备好的帧，通常是按顺序的
            // 为了和 CSV 对齐，我们手动计数循环次数作为 frameIdx

            var frameIdx = 0
            while (true) {
                val frame = grabber.grabImage() ?: break

                // 安全检查：如果目标全都找完了
                if (currentTargetIdx >= stages.size) {
                    log.info("All stages analyzed, stopping.")
                    break
                }

                val srcMat = converter.convert(frame)
                if (srcMat == null) {
                    frameIdx++
                    continue
                }

                // 转灰度
                val grayMat = Mat()
                cvtColor(srcMat, grayMat, COLOR_BGR2GRAY)

                // A. 构建查找窗口 (Search Window)
                // 逻辑：包括当前目标，以及紧随其后的所有可选目标，直到遇到下一个必选目标
                val searchWindow = mutableListOf<Pair<Int, LogicalStage>>()
                for (i in currentTargetIdx until stages.size) {
                    val stage = stages[i]
                    searchWindow.add(i to stage)
                    if (!stage.isOptional) {
                        break
                    }
                }

                // B. 在窗口内并行匹配 (寻找 Frame 内的最佳匹配)
                var bestScoreInFrame = -1.0
                var bestStageInFrame: LogicalStage? = null
                var bestStageIdxInFrame = -1

                // 临时 Mat 用于存储匹配结果，避免内存泄漏
                val resultMat = Mat()

                for ((idx, stage) in searchWindow) {
                    // 对该 Stage 的所有变体 (Templates) 进行匹配，取最高分
                    var bestScoreForStage = -1.0

                    if (launcherMatcher != null && stage.name == launcherLoadedName) {
                        val result = launcherMatcher.evaluate(grayMat)
                        if (result.isLoaded) {
                            log.info(
                                "[Frame $frameIdx] ✅ Launcher loaded matched: " +
                                    "loaded=%.3f loading=%.3f edge=%.4f".format(
                                        result.loadedScore, result.loadingScore, result.edgeScore
                                    )
                            )
                        }
                        bestScoreForStage = if (result.isLoaded) 1.0 else -1.0
                    } else for (variation in stage.variations) {
                        // 如果是亮度检查模式，用亮度代替模板匹配
                        if (variation.isBrightnessCheck) {
                            val meanBrightness = mean(grayMat)[0]
                            if (meanBrightness <= variation.maxBrightness) {
                                bestScoreForStage = 0.99  // 固定高分表示匹配
                                log.debug("Brightness check passed: ${meanBrightness.toInt()} <= ${variation.maxBrightness}")
                            }
                            continue
                        }

                        // 检查 minBrightness 参数 (用于其他检测)
                        if (variation.minBrightness > 0) {
                            val meanBrightness = mean(grayMat)[0]
                            if (meanBrightness < variation.minBrightness) {
                                log.debug("Frame brightness (${meanBrightness.toInt()}) below min_brightness (${variation.minBrightness}), skipping")
                                continue
                            }
                        }

                        // 标准模板匹配
                        matchTemplate(grayMat, variation.tpl, resultMat, TM_CCOEFF_NORMED)

                        // minMaxLoc
                        val minVal = DoubleArray(1)
                        val maxVal = DoubleArray(1)
                        minMaxLoc(resultMat, minVal, maxVal, null, null, Mat())

                        val score = maxVal[0]
                        if (score > bestScoreForStage) {
                            bestScoreForStage = score
                        }
                    }

                    // 如果这个 Stage 的得分比当前帧里其他 Stage 都高，记录它
                    if (bestScoreForStage > bestScoreInFrame) {
                        bestScoreInFrame = bestScoreForStage
                        bestStageInFrame = stage
                        bestStageIdxInFrame = idx
                    }
                }

                // 释放 OpenCV 资源 (重要！否则内存泄漏)
                grayMat.release()
                resultMat.release()
                // srcMat 是 converter 内部引用的，通常不需要手动 release，但 frame 需要被 GC 处理

                // C. 检查最佳匹配是否满足阈值
                val threshold = if (bestStageInFrame?.name == launcherLoadedName) {
                    0.99
                } else {
                    bestStageInFrame?.variations?.firstOrNull()?.threshold ?: 0.8
                }
                if (bestScoreInFrame >= threshold && bestStageInFrame != null) {
                    val ts = frameTimeMap[frameIdx] ?: 0.0

                    // 记录数据
                    bestStageInFrame.foundAtTs = ts
                    bestStageInFrame.foundAtFrame = frameIdx

                    if (bestStageInFrame.name == launcherLoadedName) {
                        log.info("[Frame $frameIdx] 🚀 Launcher loaded confirmed")
                    } else {
                        log.info("[Frame $frameIdx] 🎯 Captured: ${bestStageInFrame.name} (Score: %.3f)".format(bestScoreInFrame))
                    }

                    // 指针跳转
                    currentTargetIdx = bestStageIdxInFrame + 1

                    if (currentTargetIdx < stages.size) {
                        // 预览下一个窗口
                        val nextWindowNames = mutableListOf<String>()
                        for (i in currentTargetIdx until stages.size) {
                            nextWindowNames.add(stages[i].name)
                            if (!stages[i].isOptional) break
                        }
                        log.info("--> New search window: $nextWindowNames")
                    }
                }

                frameIdx++
                // 每隔一定帧数打印一下进度，防止假死
                if (frameIdx % 500 == 0) {
                    log.info("Processing: $frameIdx / $totalFrames")
                }
            }

            log.info("") // 换行
            grabber.stop()
            grabber.release()

            printReport(reportPath)
        }

        // 4. 生成报告
        private fun printReport(reportPath: File) {
            val sb = StringBuilder()
            sb.append("\n# BOOT PERFORMANCE REPORT\n\n")
            // 表头
            sb.append("| Stage Name | EpochTime | Timestamp | Delta (s) | Frame |\n")
            sb.append("|---|---|---|---|---|\n")

            // 查找 Boot Logo 时间作为基准
            val bootLogoStage = stages.find { it.name == "Boot Logo" }
            val bootLogoTs = bootLogoStage?.foundAtTs

            val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault())

            for (stage in stages) {
                val ts = stage.foundAtTs
                val frameNum = stage.foundAtFrame

                var tsDisplay = "Not Found"
                var readableTimeStr = "N/A"
                var deltaStr = "N/A"
                var frameDisplay = "N/A"

                if (ts != null) {
                    tsDisplay = String.format("%.3f", ts)

                    // Readable Time
                    try {
                        val instant = Instant.ofEpochMilli((ts * 1000).toLong())
                        readableTimeStr = df.format(instant)
                    } catch (e: Exception) {
                        readableTimeStr = "Invalid TS"
                    }

                    // Delta
                    if (bootLogoTs != null) {
                        val diff = ts - bootLogoTs
                        deltaStr = String.format("%.3f", diff)
                    } else {
                        deltaStr = "N/A"
                    }

                    frameDisplay = frameNum.toString()
                } else if (bootLogoTs == null && stage.name != "Boot Logo") {
                    deltaStr = "N/A (No Logo)"
                }

                sb.append(
                    String.format(
                        "| %-28s | %-13s | %-23s | %-9s | %-5s |\n",
                        stage.name, tsDisplay, readableTimeStr, deltaStr, frameDisplay
                    )
                )
            }

            val finalReport = sb.toString()
            (finalReport)

            try {
                reportPath.writeText(finalReport)
                log.info("Report saved to: ${reportPath.absolutePath}")
            } catch (e: Exception) {
                log.error("[Error] Failed to save report: ${e.message}")
            }
        }

        private data class LauncherMatchResult(
            val loadedScore: Double,
            val loadingScore: Double,
            val edgeScore: Double,
            val isLoaded: Boolean
        )

        private inner class LauncherMatcher(
            val loadedTop: List<Mat>,
            val loadingTop: List<Mat>,
            val simThreshold: Double,
            val edgeThreshold: Double
        ) {
            fun evaluate(grayFrame: Mat): LauncherMatchResult {
                val top = cropTop(grayFrame, launcherTopRatio)
                val lower = cropLower(grayFrame, launcherLowerRatio)
                val loadedScore = loadedTop.maxOfOrNull { matchScore(top, it) } ?: -1.0
                val loadingScore = loadingTop.maxOfOrNull { matchScore(top, it) } ?: -1.0
                val edgeScore = edgeDensity(lower)
                val isLoaded = loadedScore >= simThreshold &&
                    edgeScore >= edgeThreshold &&
                    (loadedScore - loadingScore) >= launcherSimMargin
                return LauncherMatchResult(loadedScore, loadingScore, edgeScore, isLoaded)
            }
        }

        private fun buildLauncherMatcher(): LauncherMatcher? {
            val loadingStage = stages.find { it.name == launcherStartName }
            val loadedStage = stages.find { it.name == launcherLoadedName }
            if (loadingStage == null || loadedStage == null) {
                return null
            }

            val loadingTop = loadingStage.variations.map { cropTop(it.tpl, launcherTopRatio) }
            val loadedTop = loadedStage.variations.map { cropTop(it.tpl, launcherTopRatio) }
            val simThreshold = calibrateSimThreshold(loadedTop, loadingTop)
            val edgeThreshold = calibrateEdgeThreshold(loadedStage.variations.map { it.tpl }, loadingStage.variations.map { it.tpl })

            val loadingFiles = loadingStage.variations.joinToString(", ") { it.file }
            val loadedFiles = loadedStage.variations.joinToString(", ") { it.file }
            log.info("Launcher matcher loading templates: $loadingFiles")
            log.info("Launcher matcher loaded templates: $loadedFiles")
            log.info("Launcher matcher thresholds: sim=%.3f, edge=%.4f".format(simThreshold, edgeThreshold))
            return LauncherMatcher(loadedTop, loadingTop, simThreshold, edgeThreshold)
        }

        private fun calibrateSimThreshold(loadedTop: List<Mat>, loadingTop: List<Mat>): Double {
            if (loadedTop.isEmpty() || loadingTop.isEmpty()) return 0.7
            val llScores = mutableListOf<Double>()
            for (i in loadedTop.indices) {
                for (j in (i + 1) until loadedTop.size) {
                    llScores.add(matchScore(loadedTop[i], loadedTop[j]))
                }
            }
            val lmScores = mutableListOf<Double>()
            for (l in loadedTop) {
                for (s in loadingTop) {
                    lmScores.add(matchScore(l, s))
                }
            }
            if (llScores.isEmpty() || lmScores.isEmpty()) return 0.7
            val minLoaded = llScores.minOrNull() ?: 0.7
            val maxLoading = lmScores.maxOrNull() ?: 0.0
            return if (minLoaded > maxLoading + 0.02) {
                (minLoaded + maxLoading) / 2.0
            } else {
                maxOf(0.6, (minLoaded + maxLoading) / 2.0)
            }
        }

        private fun calibrateEdgeThreshold(loaded: List<Mat>, loading: List<Mat>): Double {
            if (loaded.isEmpty() || loading.isEmpty()) return 0.015
            val loadedVals = loaded.map { edgeDensity(cropLower(it, launcherLowerRatio)) }
            val loadingVals = loading.map { edgeDensity(cropLower(it, launcherLowerRatio)) }
            val minLoaded = loadedVals.minOrNull() ?: 0.0
            val maxLoading = loadingVals.maxOrNull() ?: 0.0
            return if (minLoaded > maxLoading) {
                (minLoaded + maxLoading) / 2.0
            } else {
                0.015
            }
        }

        private fun cropTop(gray: Mat, ratio: Double): Mat {
            val height = maxOf(1, (gray.rows() * ratio).toInt())
            return gray.rowRange(0, height)
        }

        private fun cropLower(gray: Mat, ratio: Double): Mat {
            val start = (gray.rows() * (1.0 - ratio)).toInt()
            val safeStart = maxOf(0, minOf(start, gray.rows() - 1))
            return gray.rowRange(safeStart, gray.rows())
        }

        private fun matchScore(image: Mat, template: Mat): Double {
            val resizedTpl = if (template.rows() != image.rows() || template.cols() != image.cols()) {
                val tmp = Mat()
                resize(template, tmp, image.size())
                tmp
            } else {
                template
            }
            val result = Mat()
            matchTemplate(image, resizedTpl, result, TM_CCOEFF_NORMED)
            val minVal = DoubleArray(1)
            val maxVal = DoubleArray(1)
            minMaxLoc(result, minVal, maxVal, null, null, Mat())
            result.release()
            if (resizedTpl !== template) {
                resizedTpl.release()
            }
            return maxVal[0]
        }

        private fun edgeDensity(gray: Mat): Double {
            val edges = Mat()
            Canny(gray, edges, 50.0, 150.0)
            val count = countNonZero(edges)
            val total = edges.rows().toLong() * edges.cols().toLong()
            edges.release()
            return if (total == 0L) 0.0 else count.toDouble() / total.toDouble()
        }
    }

    fun analyzeVideo(args: Array<String>) {
// === 入口点 ===
        if (args.isEmpty()) {
            log.info("Usage: analyze video <log_dir>")
            exitProcess(1)
        }

        val logDir = args[0]
        val dirFile = File(logDir)

        if (!dirFile.exists() || !dirFile.isDirectory) {
            log.error("[Error] Directory not found: $logDir")
            exitProcess(1)
        }

        val videoPath = File(dirFile, "video.mp4")
        val csvPath = File(dirFile, "video.csv")
        val reportPath = File(dirFile, "video_report.md")

        if (!videoPath.exists()) {
            log.error("[Error] video.mp4 not found in $logDir")
            exitProcess(1)
        }
        if (!csvPath.exists()) {
            log.error("[Error] video.csv not found in $logDir")
            exitProcess(1)
        }

// 运行
        val profiler = BootProfiler(RES_DIR, DESC_FILE)
        profiler.loadCsvTimestamps(csvPath)
        profiler.runAnalysis(videoPath, reportPath)
    }
    fun analyzeLog(args: Array<String>) {
        if (args.isEmpty()) {
            log.info("Usage: analyzeLog <log_dir>")
            return
        }
        val logDir = args[0]
        log.info("Analyzing log: $logDir")
        val consoleLogFile = File(logDir, "console.log")
        val logcatFile = File(logDir, "logcat.lc")
        val outputFile = File(logDir, "log_report.md")

        val startTimestamp = getLastHwInitDdrTimestamp(consoleLogFile)
        if (startTimestamp != null) {
            log.info("Found last 'DHL PT:' at: $startTimestamp, starting analysis from this point")
        } else {
            log.warn("No 'DHL PT:' found, analyzing from the beginning")
        }

        val consoleLines = if (consoleLogFile.exists()) consoleLogFile.readLines() else emptyList()
        val logcatLines = if (logcatFile.exists()) logcatFile.readLines() else emptyList()

        val timestampPattern = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]")
        val filteredConsoleLines = if (startTimestamp == null) consoleLines else consoleLines.filter {
            val match = timestampPattern.find(it)
            match != null && match.groupValues[1] >= startTimestamp
        }
        val filteredLogcatLines = if (startTimestamp == null) logcatLines else logcatLines.filter {
            val match = timestampPattern.find(it)
            match != null && match.groupValues[1] >= startTimestamp
        }

        val logcatPatterns = loadLogcatPatterns()
        val deviceInfo = parseDeviceInfo(filteredLogcatLines)
        val consoleEvents = parseConsoleLog(filteredConsoleLines)
        val (logcatEvents, notFound) = parseLogcatFile(filteredLogcatLines, logcatPatterns)
        val allEvents = (consoleEvents + logcatEvents).sortedBy { it.timestamp }

        generateMarkdownReport(allEvents, notFound, deviceInfo, outputFile)
        log.info("Analysis complete, report generated at: '${outputFile.absolutePath}'")
    }

    private fun getLastHwInitDdrTimestamp(file: File): String? {
        if (!file.exists()) {
            return null
        }
        val ddrPattern = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+.*(DHL PT:.*)")
        var lastTimestamp: String? = null
        file.forEachLine { line ->
            ddrPattern.find(line)?.let {
                lastTimestamp = it.groupValues[1]
            }
        }
        return lastTimestamp
    }

    private fun loadLogcatPatterns(): List<EventPattern> {
        val patterns = mutableListOf<EventPattern>()
        val patternFile = File(LOG_PATTERN_FILE)
        if (!patternFile.exists()) {
            log.warn("Warning: Log pattern file not found: ${patternFile.absolutePath}")
            return patterns
        }

        patternFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val parts = trimmed.split('|', limit = 3).map { it.trim() }
                if (parts.size == 3) {
                    val eventName = parts[0]
                    val singleOccurrence = parts[1].toBoolean()
                    val regex = parts[2]
                    patterns.add(EventPattern(eventName, Regex.fromLiteral(regex), singleOccurrence))
                }
            }
        }
        return patterns
    }

    private data class FoundEvent(val timestamp: String, val eventName: String, val logLine: String)

    private data class EventPattern(val eventName: String, val pattern: Regex, val isSingleOccurrence: Boolean = true)

    private fun parseConsoleLog(lines: List<String>): List<FoundEvent> {
        if (lines.isEmpty()) {
            return emptyList()
        }

        val kernelPattern = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+.*(Start kernel at.*)")
        val ddrPattern = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+.*(DHL PT:.*)")
        var kernelStartEvent: FoundEvent? = null
        var ddrInitEvent: FoundEvent? = null

        lines.forEach { line ->
            kernelPattern.find(line)?.let {
                kernelStartEvent = FoundEvent(it.groupValues[1], "Kernel Start", line.trim())
            }
            ddrPattern.find(line)?.let {
                ddrInitEvent = FoundEvent(it.groupValues[1], "HW Init", line.trim())
            }
        }

        log.info("parseConsoleLog: " + ddrInitEvent.toString())
        return listOfNotNull(ddrInitEvent, kernelStartEvent)
    }

    private fun parseLogcatFile(lines: List<String>, logcatPatterns: List<EventPattern>): Pair<List<FoundEvent>, List<EventPattern>> {
        if (lines.isEmpty()) {
            return Pair(emptyList(), logcatPatterns.filter { it.isSingleOccurrence })
        }

        val foundEvents = mutableListOf<FoundEvent>()
        val patternsToCheck = logcatPatterns.toMutableList()
        val timestampPattern = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+(.*)")

        lines.forEach { line ->
            timestampPattern.matchEntire(line.trim())?.let { match ->
                val timestamp = match.groupValues[1]
                val logContent = match.groupValues[2]

                val patternsIterator = patternsToCheck.iterator()
                while (patternsIterator.hasNext()) {
                    val eventDef = patternsIterator.next()
                    eventDef.pattern.find(logContent)?.let {
                        var eventName = eventDef.eventName
                        if (eventDef.eventName == "Manual Event" && it.groupValues.size > 1) {
                            eventName = "$eventName: ${it.groupValues[1].trim()}"
                        }
                        foundEvents.add(FoundEvent(timestamp, eventName, line.trim()))
                        if (eventDef.isSingleOccurrence) {
                            patternsIterator.remove()
                        }
                    }
                }
            }
        }
        return Pair(foundEvents, patternsToCheck.filter { it.isSingleOccurrence })
    }

    private fun parseDeviceInfo(lines: List<String>): Map<String, String> {
        if (lines.isEmpty()) return emptyMap()

        val deviceInfo = mutableMapOf<String, String>()
        val propPattern = Regex("\\[(ro\\.(?:product\\.(?:model|device)))\\]: \\[([^\\]]+)\\]")
        val fingerprintPattern = Regex("-Xfingerprint:(.*)")
        val desiredProps = setOf("ro.product.model", "ro.product.device")

        for (line in lines) {
            propPattern.find(line)?.let {
                val key = it.groupValues[1]
                if (desiredProps.contains(key)) {
                    deviceInfo[key] = it.groupValues[2]
                }
            }
            fingerprintPattern.find(line)?.let {
                deviceInfo["ro.build.fingerprint"] = it.groupValues[1].trim()
            }
            if (deviceInfo.keys.containsAll(desiredProps) && deviceInfo.containsKey("ro.build.fingerprint")) {
                return deviceInfo //early exit
            }
        }
        return deviceInfo
    }

    private fun generateMarkdownReport(
        allEvents: List<FoundEvent>,
        notFoundLogcat: List<EventPattern>,
        deviceInfo: Map<String, String>,
        outputFile: File
    ) {
        val timeZeroA = allEvents.find { it.eventName == "HW Init" }?.let {
            Instant.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()).parse(it.timestamp))
        }
        val timeZeroB = allEvents.find { it.eventName == "Kernel Start" }?.let {
            Instant.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()).parse(it.timestamp))
        }

        outputFile.bufferedWriter().use { writer ->
            writer.write("# Android Boot Timeline Analysis Report\n\n")

            if (deviceInfo.isNotEmpty()) {
                writer.write("## Device and Build Information\n\n")
                writer.write("- **Model:** `${deviceInfo.getOrDefault("ro.product.model", "N/A")}`\n")
                writer.write("- **Device:** `${deviceInfo.getOrDefault("ro.product.device", "N/A")}`\n")
                writer.write("- **Fingerprint:** `${deviceInfo.getOrDefault("ro.build.fingerprint", "N/A")}`\n\n")
            }

            writer.write("Source: `console.log` and `logcat.lc`\n\n")

            if (allEvents.isEmpty()) {
                writer.write("No key events found in the log files.\n")
                return
            }

            writer.write("## Timeline Summary\n\n")
            writer.write("`Delta(A)`: Relative time based on \"HW Init\"\n")
            writer.write("`Delta(B)`: Relative time based on \"Kernel Start\"\n\n")
            writer.write("| Timestamp          | Delta(A) | Delta(B) | Key Event                     |\n")
            writer.write("|-----------------------------|-----------|-----------|------------------------------------------|\n")

            allEvents.forEach { event ->
                val currentTime = Instant.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault()).parse(event.timestamp))

                val deltaAStr = timeZeroA?.let { "%.1fs".format((currentTime.toEpochMilli() - it.toEpochMilli()) / 1000.0) } ?: "N/A"
                val deltaBStr = timeZeroB?.let { "%.1fs".format((currentTime.toEpochMilli() - it.toEpochMilli()) / 1000.0) } ?: "N/A"

                writer.write(
                    "| `${event.timestamp}` | `$deltaAStr` | `$deltaBStr` | ${event.eventName} |\n"
                )
            }
            writer.write("\n")

            writer.write("## Detailed Event Log\n\n")
            allEvents.forEach { event ->
                writer.write("### ${event.eventName}\n\n")
                writer.write("- **Timestamp:** `${event.timestamp}`\n")
                writer.write("- **Related Log:**\n")
                writer.write("  ```log\n")
                writer.write("  ${event.logLine}\n")
                writer.write("  ```\n\n")
            }

            if (notFoundLogcat.isNotEmpty()) {
                writer.write("## Events not found in Logcat\n\n")
                writer.write("The following key events were not found in `logcat.lc`:\n\n")
                notFoundLogcat.forEach { writer.write("- ${it.eventName}\n") }
            }
        }
    }

    private fun parseMarkdownReport(
        reportFile: File,
        isLogReport: Boolean
    ): Pair<String, List<MergedTimelineEvent>> {
        if (!reportFile.exists()) return "" to emptyList()

        val events = mutableListOf<MergedTimelineEvent>()
        val lines = reportFile.readLines()
        val tableSeparatorIndex = lines.indexOfFirst { it.startsWith("|") && it.contains("---") }

        if (tableSeparatorIndex == -1) { // No table found
            return lines.joinToString("\n") to emptyList()
        }

        // Preamble is everything before the table header line
        val preambleLines = lines.subList(0, tableSeparatorIndex - 1)
        val preamble = preambleLines.joinToString("\n")

        // Table rows are everything after the separator
        val tableLines = lines.subList(tableSeparatorIndex + 1, lines.size)

        for (line in tableLines) {
            if (!line.startsWith("|")) continue // Skip non-table lines

            val parts = line.split("|").map { it.trim() }
            if (parts.size < 4) continue // Malformed row

            try {
                if (isLogReport) {
                    // Log report format: | `timestamp` | `deltaA` | `deltaB` | eventName |
                    if (parts.size >= 5) {
                        val timestamp = parts[1].replace("`", "")
                        val deltaA = parts[2].replace("`", "")
                        val deltaB = parts[3].replace("`", "")
                        val eventName = parts[4]
                        if (timestamp.isNotBlank() && timestamp != "N/A") {
                            events.add(
                                MergedTimelineEvent(
                                    timestamp = timestamp,
                                    source = "Log",
                                    eventName = eventName,
                                    deltaA = deltaA,
                                    deltaB = deltaB
                                )
                            )
                        }
                    }
                } else {
                    // Video report format: | stageName | epochTime | TimeStamp | delta | frame |
                    if (parts.size >= 6) {
                        val eventName = parts[1]
                        val timestamp = parts[3]
                        val delta = parts[4] // This is delta from boot logo
                        val frame = parts[5]
                        if (timestamp.isNotBlank() && timestamp != "N/A") {
                            events.add(
                                MergedTimelineEvent(
                                    timestamp = timestamp,
                                    source = "Video",
                                    eventName = eventName,
                                    deltaA = delta, // Re-using deltaA for video's delta
                                    frame = frame
                                )
                            )
                        }
                    }
                }
            } catch (e: IndexOutOfBoundsException) {
                log.warn("Could not parse table row: $line")
            }
        }

        return preamble to events
    }


    fun mergeReports(args: Array<String>) {
        if (args.isEmpty()) {
            log.info("Usage: mergeReports <log_dir>")
            return
        }
        log.info("mergeReports: $args")
        val logDir = args[0]
        val inVideoReport = File(logDir, "video_report.md")
        val inLogReport = File(logDir, "log_report.md")
        val mergedReportFile = File(logDir, "merged_boot_report.md")

        val (videoPreamble, videoEvents) = parseMarkdownReport(inVideoReport, isLogReport = false)
        val (logPreamble, logEvents) = parseMarkdownReport(inLogReport, isLogReport = true)

        val allEvents = (videoEvents + logEvents).sortedBy { it.timestamp }

        val timeZeroA = allEvents.find { it.eventName.trim() == "HW Init" }?.let {
            try {
                Instant.from(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
                        .parse(it.timestamp)
                )
            } catch (e: Exception) {
                null
            }
        }
        val timeZeroB = allEvents.find { it.eventName.trim() == "Kernel Start" }?.let {
            try {
                Instant.from(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
                        .parse(it.timestamp)
                )
            } catch (e: Exception) {
                null
            }
        }

        val recalculatedEvents = allEvents.map { event ->
            val currentTime = try {
                Instant.from(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
                        .parse(event.timestamp)
                )
            } catch (e: Exception) {
                null
            }

            val deltaAStr = if (currentTime != null && timeZeroA != null) {
                "%.3fs".format((currentTime.toEpochMilli() - timeZeroA.toEpochMilli()) / 1000.0)
            } else {
                "-"
            }

            val deltaBStr = if (currentTime != null && timeZeroB != null) {
                "%.3fs".format((currentTime.toEpochMilli() - timeZeroB.toEpochMilli()) / 1000.0)
            } else {
                "-"
            }

            event.copy(
                deltaA = deltaAStr,
                deltaB = deltaBStr,
                frame = if (event.frame == "N/A") "-" else event.frame
            )
        }

        mergedReportFile.bufferedWriter().use { writer ->
            writer.write("# Merged Boot Timeline Analysis Report\n\n")

            // Write preambles, but remove the original titles to avoid duplication
            writer.write(videoPreamble.replace("# BOOT PERFORMANCE REPORT", "").trim())
            writer.write("\n\n")
            writer.write(logPreamble.replace("# Android Boot Timeline Analysis Report", "").trim())
            writer.write("\n\n")

            writer.write("## Merged Timeline Summary\n\n")
            writer.write("`Delta(A)`: Relative time based on \"HW Init\"\n")
            writer.write("`Delta(B)`: Relative time based on \"Kernel Start\"\n\n")

            // Unified table header
            writer.write("| TimeStamp                   | Log Event                   | Visual Event                | Delta(A)  | Delta(B)  | Frame |\n")
            writer.write("|-----------------------------|-----------------------------|-----------------------------|-----------|-----------|-------|\n")

            recalculatedEvents.forEach { event ->
                val logEvent = if (event.source == "Log") event.eventName else "-"
                val visualEvent = if (event.source == "Video") event.eventName else "-"
                writer.write(
                    String.format(
                        "| %-27s | %-27s | %-27s | %-9s | %-9s | %-5s |\n",
                        event.timestamp,
                        logEvent,
                        visualEvent,
                        event.deltaA,
                        event.deltaB,
                        event.frame
                    )
                )
            }
        }
        log.info("Merged report created at: ${mergedReportFile.absolutePath}")
    }

    companion object {
        val log = LoggerFactory.getLogger(VideoAnalyzer::class.java)
    }
}
