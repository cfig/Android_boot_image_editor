package cfig.lazybox.staging

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess

class DiffCI {
    private val FAKE_URL = "FAKE_URL"
    private val baseUrl = System.getenv("diffCIbaseUrl") ?: FAKE_URL
    private val finder = ChangelistFinder(baseUrl)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val buildIdRegex = ".*/(\\d{12})/changelist".toRegex()
    private val log = LoggerFactory.getLogger(DiffCI::class.java)

    fun run(args: Array<String>) {
        if (baseUrl == FAKE_URL) {
            log.info("Error: Environment variable 'diffCIbaseUrl' is not set.")
            log.info("Please set it to the base URL of the changelist directory.")
            exitProcess(1)
        }
        when (args.size) {
            1 -> handleSingleDate(args)
            2 -> handleRange(args)
            else -> printUsageAndExit()
        }
    }

    private fun handleSingleDate(args: Array<String>) {
        if (args[0].length != 8) {
            log.info("Error: For a single argument, please provide a date in yyyyMMdd format.")
            exitProcess(1)
        }
        val targetDate = parseDate(args[0]) ?: exitProcess(1)

        log.info("=============================================")
        log.info("Searching for changelists on: ${args[0]}")
        log.info("=============================================")

        val urls = finder.findChangelistUrlsForDate(targetDate)
        printResults(urls)
    }

    private fun handleRange(args: Array<String>) {
        val arg1 = args[0]
        val arg2 = args[1]

        if (arg1.length != arg2.length) {
            log.info("Error: Start and end arguments must be of the same format (both dates or both date-times).")
            exitProcess(1)
        }

        when (arg1.length) {
            8 -> handleDateRange(arg1, arg2)
            12 -> handleDateTimeRange(arg1, arg2)
            else -> {
                log.info("Error: Invalid argument format. Please use yyyyMMdd or yyyyMMddHHmm.")
                exitProcess(1)
            }
        }
    }

    private fun handleDateRange(startDateStr: String, endDateStr: String) {
        val startDate = parseDate(startDateStr) ?: exitProcess(1)
        val endDate = parseDate(endDateStr) ?: exitProcess(1)

        if (startDate.isAfter(endDate)) {
            log.info("Error: The start date ($startDateStr) must be before or the same as the end date ($endDateStr).")
            exitProcess(1)
        }

        log.info("=============================================")
        log.info("Searching for changelists from $startDateStr to $endDateStr")
        log.info("=============================================")

        val allUrls = mutableListOf<String>()
        var currentDate = startDate
        while (currentDate <= endDate) {
            log.info("\n----- Processing Date: $currentDate -----")
            val dailyUrls = finder.findChangelistUrlsForDate(currentDate)
            allUrls.addAll(dailyUrls)
            currentDate = currentDate.plusDays(1)
        }
        printResults(allUrls)
    }

    private fun handleDateTimeRange(startDateTimeStr: String, endDateTimeStr: String) {
        val startDateTime = parseDateTime(startDateTimeStr) ?: exitProcess(1)
        val endDateTime = parseDateTime(endDateTimeStr) ?: exitProcess(1)

        if (startDateTime.isAfter(endDateTime)) {
            log.info("Error: The start time ($startDateTimeStr) must be before or the same as the end time ($endDateTimeStr).")
            exitProcess(1)
        }

        log.info("=============================================")
        log.info("Searching for changelists between $startDateTimeStr and $endDateTimeStr")
        log.info("=============================================")

        val allUrls = mutableListOf<String>()
        var currentDate = startDateTime.toLocalDate()
        while (currentDate <= endDateTime.toLocalDate()) {
            log.info("\n----- Processing Date: $currentDate -----")
            val dailyUrls = finder.findChangelistUrlsForDate(currentDate)

            val filteredUrls = dailyUrls.filter { url ->
                val matchResult = buildIdRegex.find(url)
                if (matchResult != null) {
                    val buildId = matchResult.groupValues[1]
                    val buildDateTime = parseDateTime(buildId)
                    buildDateTime != null && !buildDateTime.isBefore(startDateTime) && !buildDateTime.isAfter(endDateTime)
                } else {
                    false
                }
            }
            allUrls.addAll(filteredUrls)
            currentDate = currentDate.plusDays(1)
        }
        printResults(allUrls, isTimeRange = true)
    }

    private fun printUsageAndExit() {
        log.info("Error: Invalid number of arguments.")
        log.info("\nUsage:")
        log.info("  Single Date:   --args='diffci <yyyymmdd>'")
        log.info("  Date Range:    --args='diffci <start_date> <end_date>'")
        log.info("  Time Range:    --args='diffci <start_datetime> <end_datetime>'")
        log.info("\nExamples:")
        log.info("  --args='diffci  20250628'")
        log.info("  --args='diffci  20250627 20250628'")
        log.info("  --args='diffci  202506281000 202506281430'")
        exitProcess(1)
    }

    private fun printResults(urls: List<String>, isTimeRange: Boolean = false) {
        log.info("\n--- Results ---")
        if (urls.isNotEmpty()) {
            log.info("Successfully found ${urls.size} changelist files:")
            urls.forEach { log.info(it) }
        } else {
            val rangeType = if (isTimeRange) "time range" else "date(s)"
            log.info("No changelist files were found for the specified $rangeType.")
        }
        log.info("---------------")
    }

    private fun parseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr, dateFormat)
        } catch (e: DateTimeParseException) {
            log.info("Error: Invalid date format for '$dateStr'.")
            log.info("Please use the yyyyMMdd format (e.g., 20250628).")
            null
        }
    }

    private fun parseDateTime(dateTimeStr: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(dateTimeStr, dateTimeFormat)
        } catch (e: DateTimeParseException) {
            log.info("Error: Invalid date-time format for '$dateTimeStr'.")
            log.info("Please use the yyyyMMddHHmm format (e.g., 202506281027).")
            null
        }
    }

    /**
     * Inner class to handle the web scraping logic.
     */
    private inner class ChangelistFinder(private val baseUrl: String) {
        private val client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

        fun findChangelistUrlsForDate(date: LocalDate): List<String> {
            val yearMonthPattern = DateTimeFormatter.ofPattern("yyyyMM")
            val yearMonthDayPattern = DateTimeFormatter.ofPattern("yyyyMMdd")
            val yearMonthStr = date.format(yearMonthPattern)
            val yearMonthDayStr = date.format(yearMonthDayPattern)
            val directoryUrl = "$baseUrl/$yearMonthStr/$yearMonthDayStr/"

            return try {
                val htmlContent = fetchUrlContent(directoryUrl)
                if (htmlContent != null) {
                    parseDirectoryHtmlWithRegex(htmlContent, directoryUrl, yearMonthDayStr)
                } else {
                    emptyList()
                }
            } catch (e: IOException) {
                log.info("An error occurred during the network request: ${e.message}")
                emptyList()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.info("The network request was interrupted: ${e.message}")
                emptyList()
            }
        }

        private fun fetchUrlContent(url: String): String? {
            try {
                val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() != 200) {
                    if (response.statusCode() != 404) {
                        log.info("Request for $url failed with code: ${response.statusCode()}")
                    }
                    return null
                }
                return response.body()
            } catch (e: Exception) {
                log.info("Exception while fetching URL '$url': ${e.message}")
                return null
            }
        }

        private fun parseDirectoryHtmlWithRegex(html: String, directoryUrl: String, datePrefix: String): List<String> {
            val regex = "href=\"($datePrefix\\d+)/\"".toRegex()
            return regex.findAll(html)
                .map { matchResult ->
                    val dirName = matchResult.groupValues[1]
                    "$directoryUrl$dirName/changelist"
                }
                .toList()
        }
    }
}
