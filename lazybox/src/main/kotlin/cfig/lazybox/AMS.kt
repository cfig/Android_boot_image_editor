package cfig.lazybox

import cfig.lazybox.sysinfo.SysInfo.Companion.runAndWrite
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AMS {
    enum class StandbyBucket(val id: Int) {
        STANDBY_BUCKET_EXEMPTED(5),
        STANDBY_BUCKET_ACTIVE(10),
        STANDBY_BUCKET_WORKING_SET(20),
        STANDBY_BUCKET_FREQUENT(30),
        STANDBY_BUCKET_RARE(40),
        STANDBY_BUCKET_RESTRICTED(45),
        STANDBY_BUCKET_NEVER(50);

        companion object {
            fun fromValue(value: Int): StandbyBucket? = StandbyBucket.entries.firstOrNull { it.id == value }
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(AMS::class.qualifiedName)
        fun getProcRank(): MutableList<String> {
            val pkgRank: MutableList<String> = mutableListOf()
            ByteArrayOutputStream().use {
                runAndWrite("adb shell procrank", it, true)
                it.toString().split("\n").subList(1, 21).forEachIndexed { i, line ->
                    val pkg = line.trim().split("\\s+".toRegex()).last()
                    pkgRank.add(pkg)
                }
            }

            //print pkgRank items, with index and content

            FileOutputStream("proc_rank.log").use {
                pkgRank.forEachIndexed { i, s ->
                    it.write("$s\n".toByteArray())
                }
            }
            return pkgRank
        }

        fun getStandbyBucket(): HashMap<StandbyBucket, MutableList<String>> {
            val buckets: HashMap<StandbyBucket, MutableList<String>> = hashMapOf(
                StandbyBucket.STANDBY_BUCKET_EXEMPTED to mutableListOf(),
                StandbyBucket.STANDBY_BUCKET_ACTIVE to mutableListOf(),
                StandbyBucket.STANDBY_BUCKET_WORKING_SET to mutableListOf(),
                StandbyBucket.STANDBY_BUCKET_FREQUENT to mutableListOf(),
                StandbyBucket.STANDBY_BUCKET_RARE to mutableListOf(),
                StandbyBucket.STANDBY_BUCKET_RESTRICTED to mutableListOf(),
                StandbyBucket.STANDBY_BUCKET_NEVER to mutableListOf(),
            )
            ByteArrayOutputStream().use {
                runAndWrite("adb shell am get-standby-bucket", it, true)
                it.toString().trim().split("\n").forEachIndexed { i, line ->
                    log.info("#$i: $line")
                    if (line.split(":").size == 2) {
                        val b = line.split(":").get(0).trim()
                        val a = line.split(":").get(1).trim()
                        log.info("[$a]-[$b]")
                        buckets[StandbyBucket.fromValue(a.toInt())]!!.add(b)
                    }
                }
            }
            StandbyBucket.entries.forEach {
                log.info(it.toString() + "(${it.id})")
                buckets[it]!!.apply { sort() }.forEach {
                    log.info("\t$it")
                }
            }
            return buckets
        }

        fun getStandbyBucket2(): HashMap<String, StandbyBucket> {
            val ret: HashMap<String, StandbyBucket> = HashMap<String, StandbyBucket>()
            ByteArrayOutputStream().use {
                runAndWrite("adb shell am get-standby-bucket", it, true)
                it.toString().trim().split("\n").forEachIndexed { i, line ->
                    log.info("#$i: $line")
                    if (line.split(":").size == 2) {
                        val b = line.split(":").get(0).trim()
                        val a = line.split(":").get(1).trim()
                        log.info("[$a]-[$b]")
                        ret.put(b, StandbyBucket.fromValue(a.toInt())!!)
                    }
                }
            }
            return ret
        }

        fun getOom() {
            val text = ByteArrayOutputStream().use {
                runAndWrite("adb shell dumpsys activity oom", it, true)
                it.toString()
            }
            log.info(text)
            val lines = text.trim().split("\n") // Split lines
            val regex = Regex("""^ +Proc #\d+: (.*?) +oom: max=\d+ curRaw=(-?\d+) setRaw=(-?\d+) cur=(-?\d+) set=(-?\d+)""") // Match relevant parts
            lines.forEach { line ->
                regex.matchEntire(line)?.let { matchResult ->
                    val groups = matchResult.groups
                    // Extract data from groups
                    val packageName = groups[1]?.value ?: ""
                    val oomCurValue = groups[2]?.value?.toIntOrNull() ?: 0
                    val status = groups[3]?.value ?: ""
                    log.info("$packageName -> $oomCurValue -> $status")
                }
            }
        }


        fun computeRankAndBucket(rank: MutableList<String>, bkt: HashMap<String, StandbyBucket>) {
            val sb = StringBuilder()
            rank.forEach {
                val bktEntry = bkt.get(it)
                if (bktEntry != null) {
                    sb.append(String.format("%-40s %s\n", it, bktEntry))
                } else {
                    sb.append(String.format("%-40s -\n", it))
                }
            }
            log.info("Writing to rank_bucket.log ...\n$sb")
            File("rank_bucket.log").writeText(sb.toString())
        }

    }
}