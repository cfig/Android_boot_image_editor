import org.junit.Test
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

class ReadTest {
    data class Trigger(
            var trigger: String = "",
            var actions: MutableList<String> = mutableListOf()
    )

    data class Import(
            var initrc: String = ""
    )

    data class Service(
            var name: String = "",
            var cmd: String = "",
            var theClass: String = "default",
            var theUser: String? = null,
            var theGroup: String? = null,
            var theSeclabel: String? = null,
            var theMiscAttr: MutableList<String> = mutableListOf(),
            var theCaps: MutableList<String> = mutableListOf(),
            var theSocket: String? = null,
            var theWritePid: String? = null,
            var theKeycodes: String? = null,
            var thePriority: Int? = null,
            var theIOPriority: String? = null,
            var theOnRestart: MutableList<String> = mutableListOf()
    )

    fun parseConfig(inRootDir: String, inPath: String,
                    triggers: MutableList<Trigger>,
                    services: MutableList<Service>) {
        if (!File(inRootDir + inPath).exists()) {
            println("Parsing " + inPath + " fail: 404");
        }
        if (File(inRootDir + inPath).isFile()) {
            parseConfigFile(inRootDir, inPath, triggers, services)
        } else if (File(inRootDir + inPath).isDirectory()) {
            parseConfigDir(inRootDir, inPath, triggers, services)
        }
    }

    fun parseConfigDir(inRootDir: String, inPath: String,
                       triggers: MutableList<Trigger>,
                       services: MutableList<Service>) {
        println("Parsing directory $inPath ...")
        File(inRootDir + inPath).listFiles().forEach {
            parseConfig(inRootDir,
                    it.path.substring(inRootDir.length - 1),
                    triggers, services)
        }
    }

    fun parseConfigFile(inRootDir: String, inPath: String,
                        triggers: MutableList<Trigger>,
                        services: MutableList<Service>) {
        if (!File(inRootDir + inPath).exists()) {
            println("Parsing $inPath fail: 404");
            return
        }
        println("Parsing file $inPath ...")
        var imports: MutableList<Import> = mutableListOf()
        var aTrigger: Trigger? = null
        var aService: Service? = null
        var aImport: Import? = null
        var toBeContinued = false
        val lines = File(inRootDir + inPath).readLines()
        for (item in lines) {
            val line = item.trim();
            //comment
            if (line.startsWith("#") || line.isEmpty()) {
                continue
            }
            //continue
            if (toBeContinued) {
                if (line.endsWith("\\")) {
                    aService!!.cmd += " "
                    aService!!.cmd += line.substring(0, line.length - 1)
                    println("    CONTINUE:" + line.substring(0, line.length - 1))
                } else {
                    toBeContinued = false
                    aService!!.cmd += " "
                    aService!!.cmd += line
                    println("    END     :$line")
                }
                continue
            }

            val finderOn = Pattern.compile("^(on)\\s+(\\S+.*$)").matcher(line)
            val finderService = Pattern.compile("^service\\s+(\\S+)\\s+(.*$)").matcher(line)
            val finderImport = Pattern.compile("^import\\s+(\\S+)$").matcher(line)
            if (finderOn.matches() || finderService.matches() || finderImport.matches()) {
                //flush start >>
                aTrigger?.let { /* println("[add] " + aTrigger); */ triggers.add(aTrigger!!); aTrigger = null }
                aService?.let { /* println("[add] " + aService); */ services.add(aService!!); aService = null }
                aImport?.let { /* println("[add] " + aImport); */ imports.add(aImport!!); aImport = null }
                // << flush end
            }
            finderOn.reset()
            finderService.reset()
            finderImport.reset()

            if (finderOn.find()) {
                //println("  |on| " + line)
                //println("  group.cnt = " + finderOn.groupCount())
                //println("  " + line.substring(finderOn.start(), finderOn.end()))
                //println("  >" + finderOn.group(1))
                //println("  >" + finderOn.group(2))
                aTrigger = Trigger(trigger = finderOn.group(2))
            } else if (finderService.find()) {
                aService = Service()
                aService!!.name = finderService.group(1)
                aService!!.cmd = finderService.group(2)
                if (finderService.group(2).endsWith("\\")) { //remove trailing slash
                    toBeContinued = true
                    aService!!.cmd = aService!!.cmd.substring(0, aService!!.cmd.length - 1)
                }
            } else if (finderImport.find()) {
                aImport = Import()
                aImport!!.initrc = finderImport.group(1)
                if (aImport!!.initrc.startsWith("/")) {
                    aImport!!.initrc = aImport!!.initrc.substring(1)
                } else {
                    //do nothing
                }
                val ro_hardware = "\${ro.hardware}"
                val ro_zygote = "\${ro.zygote}"
                aImport!!.initrc = aImport!!.initrc.replace(ro_hardware, "sequoia")
                aImport!!.initrc = aImport!!.initrc.replace(ro_zygote, "zygote32")
            } else {
                if (aTrigger != null) {
                    aTrigger!!.actions.add(line)
                } else if (aService != null) {
                    //class
                    var bParsed = false
                    lateinit var mm: Matcher
                    if (!bParsed) {
                        mm = Pattern.compile("^class\\s+(.*)").matcher(line)
                        if (mm.matches()) {
                            aService!!.theClass = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //user
                        mm = Pattern.compile("^user\\s+(.*)").matcher(line)
                        if (mm.matches()) {
                            aService!!.theUser = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //capabilities
                        mm = Pattern.compile("^capabilities\\s+(.*)").matcher(line)
                        if (mm.matches()) {
                            aService!!.theCaps.add(mm.group(1))
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //group
                        mm = Pattern.compile("^group\\s+(.*)").matcher(line)
                        if (mm.matches()) {
                            aService!!.theGroup = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //seclabel
                        mm = Pattern.compile("^seclabel\\s+(.*)").matcher(line)
                        if (mm.matches()) {
                            aService!!.theSeclabel = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //writepid
                        mm = Pattern.compile("^writepid\\s+(.*)$").matcher(line)
                        if (mm.matches()) {
                            aService!!.theWritePid = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //onrestart
                        mm = Pattern.compile("^onrestart\\s+(.*)$").matcher(line)
                        if (mm.matches()) {
                            aService!!.theOnRestart.add(mm.group(1))
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //socket
                        mm = Pattern.compile("^socket\\s+(.*)$").matcher(line)
                        if (mm.matches()) {
                            aService!!.theSocket = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //ioprio
                        mm = Pattern.compile("^ioprio\\s+(.*)$").matcher(line)
                        if (mm.matches()) {
                            aService!!.theIOPriority = mm.group(1)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //priority
                        mm = Pattern.compile("^priority\\s+(\\S+)$").matcher(line)
                        if (mm.matches()) {
                            aService!!.thePriority = Integer.parseInt(mm.group(1))
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        //check space
                        mm = Pattern.compile("^\\S+$").matcher(line)
                        if (mm.matches()) {
                            aService!!.theMiscAttr.add(line)
                            bParsed = true
                        }
                    }
                    if (!bParsed) {
                        println("<< Dangling << $line")
                    }
                } else {
                    println("<< Dangling << $line")
                }
            }
        }

        //flush start >>
        aTrigger?.let { /* println("[add] " + aTrigger); */ triggers.add(aTrigger!!); aTrigger = null }
        aService?.let { /* println("[add] " + aService); */ services.add(aService!!); aService = null }
        aImport?.let { /* println("[add] " + aImport); */ imports.add(aImport!!); aImport = null }
        // << flush end

        imports.forEach { println(it) }

        //parse imports again
        var iteratorImport: Iterator<Import> = imports.iterator()
        while (iteratorImport.hasNext()) {
            val item: Import = iteratorImport.next()
            parseConfigFile(inRootDir, item.initrc, triggers, services)
        }
        println("Parsing file $inPath done")
    }

    fun queueEventTrigger(inServices: MutableList<Service>,
                          inTriggers: List<Trigger>, inTriggerName: String,
                          inIndent: String = "") {
        val aPre = inIndent
        inTriggers.filter { it.trigger == inTriggerName }.forEach { aTrigger ->
            println(aPre + " (on+${aTrigger.trigger})")
            aTrigger.actions.forEach { aAction ->
                aAction.executeCmd(inServices, inTriggers, aPre + "  ")
            }
        }
    }

    fun String.executeCmd(inServices: MutableList<Service>,
                          inTriggers: List<Trigger>, inIndent: String) {
        val aPre = inIndent + "  "
        if (this.startsWith("trigger ")) {
            println(aPre + "|-- " + this)
            queueEventTrigger(inServices, inTriggers, this.substring(8).trim(), aPre + "|  ")
        } else if (this.startsWith("chmod")) {
        } else if (this.startsWith("chown")) {
        } else if (this.startsWith("mkdir")) {
        } else if (this.startsWith("write")) {
        } else if (Pattern.compile("class_start\\s+\\S+").matcher(this).find()) {
            println(aPre + "|-- " + this)
            val m = Pattern.compile("class_start\\s+(\\S+)$").matcher(this)
            if (m.find()) {
                inServices
                        .filter {
                            it.theClass != null
                                    && it.theClass!!.split(" ").contains(m.group(1))
                        }
                        .forEach {
                            println(aPre + "|    \\-- Starting " + it.name + "...")
                        }
            } else {
                println("error")
            }
        } else if (this.startsWith("start")) {
            println(aPre + "|-- " + this)
            println(aPre + "|    \\-- Starting " + this.substring(5).trim() + "...")
        } else {
            println(aPre + "|-- " + this)
        }
    }

    @Test
    fun parseTest() {
        System.out.println(System.getProperty("user.dir"))
        var gTriggers: MutableList<Trigger> = mutableListOf()
        var gServices: MutableList<Service> = mutableListOf()

        parseConfig("__temp/", "/init.rc", gTriggers, gServices)
        parseConfig("__temp/", "/system/etc/init", gTriggers, gServices)
        parseConfig("__temp/", "/vendor/etc/init", gTriggers, gServices)
        parseConfig("__temp/", "/odm/etc/init", gTriggers, gServices)

        gTriggers.forEach { println(it) }
        gServices.forEach { println(it) }

        println("Trigger count:" + gTriggers.size)
        println("Service count:" + gServices.size)

        queueEventTrigger(gServices, gTriggers, "early-init")
        queueEventTrigger(gServices, gTriggers, "init")
        queueEventTrigger(gServices, gTriggers, "late-init")
//        println(">> mount_all() returned 0, trigger nonencrypted")
//        queueEventTrigger(gServices, gTriggers, "nonencrypted")
    }
}