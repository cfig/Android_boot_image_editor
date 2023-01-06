#!/usr/bin/env python3

import shutil, os.path, json, subprocess, hashlib, glob
import unittest, logging, sys, lzma, time, platform

successLogo = """
      +----------------------------------+
      |    All test cases have PASSED    |
      +----------------------------------+
"""
resDir = "src/integrationTest/resources"
resDir2 = "src/integrationTest/resources_2"
log = logging.getLogger('TEST')
log.setLevel(logging.DEBUG)
consoleHandler = logging.StreamHandler(sys.stdout)
consoleHandler.setFormatter(logging.Formatter(fmt='%(asctime)s %(levelname)-8s %(name)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'))
log.addHandler(consoleHandler)

def hashFile(fileName):
    hasher = hashlib.md5()
    with open(fileName, 'rb') as afile:
        buf = afile.read()
        hasher.update(buf)
    return hasher.hexdigest()

def deleteIfExists(inFile):
    if os.path.isfile(inFile):
        log.info("rm %s" % inFile)
        raise
    ## do not delete
    #for i in range(3):
    #    try:
    #        if os.path.isfile(inFile):
    #            log.info("rm %s" % inFile)
    #            os.remove(inFile)
    #        return
    #    except Exception as e:
    #        log.warning("Exception in cleaning up %s" % inFile)
    #        time.sleep(3)

def cleanUp():
    log.info("clean up ...")
    shutil.rmtree("build/unzip_boot", ignore_errors = True)
    [deleteIfExists(item) for item in [
        "boot.img", "boot.img.clear", "boot.img.google", "boot.img.signed", "boot.img.signed2",
        "recovery.img", "recovery.img.clear", "recovery.img.google", "recovery.img.signed", "recovery.img.signed2",
        "vbmeta.img", "vbmeta.img.signed",
        "dtbo.img", "dtbo.img.clear", "dtbo.img.signed", "dtbo.img.signed2",
        "misc.img", "misc.img.new",
        "payload.bin",
        "init_boot.img", "init_boot.img.signed",
        "vendor_boot.img", "vendor_boot.img.clear", "vendor_boot.img.google", "vendor_boot.img.signed", "vendor_boot.img.signed2",
        "boot-debug.img", "boot-debug.img.clear", "boot-debug.img.google",
        "vendor_boot-debug.img", "vendor_boot-debug.img.clear", "vendor_boot-debug.img.google" ]]

def verifySingleJson(jsonFile, func = None):
    log.info("executing %s ..." % jsonFile)
    imgDir = os.path.dirname(jsonFile)
    verifyItems = json.load(open(jsonFile))
    for k, v in verifyItems["copy"].items():
        it = os.path.join(imgDir, k)
        if (os.path.isfile(it)):
            log.info("copy file: %s -> %s" % (os.path.join(imgDir, k), v))
            shutil.copyfile(it, v)
        elif (os.path.isfile(it + ".xz")):
            log.info("extract file: %s -> %s" % (it + ".xz", v))
            decompressXZ(it + ".xz", v)
        else:
            raise
    if sys.platform == "win32":
        gradleWrapper = "gradlew.bat"
    else:
        gradleWrapper = "./gradlew"
    subprocess.check_call(gradleWrapper + " unpack", shell = True)
    if func:
        func()
    subprocess.check_call(gradleWrapper + " pack", shell = True)
    for k, v in verifyItems["hash"].items():
        log.info("%s : %s" % (k, v))
        unittest.TestCase().assertIn(hashFile(k), v.split())
    try:
        subprocess.check_call(gradleWrapper + " clear", shell = True)
    except Exception as e:
        pass

def verifySingleDir(inResourceDir, inImageDir):
    resDir = inResourceDir
    imgDir = inImageDir
    log.info("Enter %s ..." % os.path.join(resDir, imgDir))
    jsonFiles = glob.glob(os.path.join(resDir, imgDir) + "/*.json")
    for jsonFile in jsonFiles:
        cleanUp()
        verifySingleJson(jsonFile)
        cleanUp()
    pyFiles = glob.glob(os.path.join(resDir, imgDir) + "/*.py")
    for pyFile in pyFiles:
        cleanUp()
        log.warning("calling %s" % pyFile)
        if sys.platform == "win32":
            theCmd = "python " + pyFile
        else:
            theCmd = pyFile
        subprocess.check_call(theCmd, shell = True)
        cleanUp()
    log.info("Leave %s" % os.path.join(resDir, imgDir))

def decompressXZ(inFile, outFile):
    with lzma.open(inFile) as f:
        file_content = f.read()
        with open(outFile, "wb") as f2:
            f2.write(file_content)

def seekedCopy(inFile, outFile, offset):
    print(inFile + " -> " + outFile)
    with open(inFile, "rb") as reader:
        reader.seek(offset)
        content = reader.read()
        with open(outFile, "wb") as writer:
            writer.write(content)

def main():
    #########################################
    #   resource_1
    #########################################
    # from volunteers
    verifySingleDir(resDir, "recovery_image_from_s-trace")
    verifySingleDir(resDir, "boot_img_from_gesangtome") # android 9, no ramdisk
    verifySingleDir(resDir, "issue_47")
    verifySingleDir(resDir, "issue_52")
    verifySingleDir(resDir, "issue_54")
    # 5.0
    verifySingleDir(resDir, "5.0_fugu_lrx21m")
    # 6.0
    verifySingleDir(resDir, "6.0.0_bullhead_mda89e")
    # 7.0 special boot
    cleanUp()
    #subprocess.check_call("dd if=%s/7.1.1_volantis_n9f27m/boot.img of=boot.img bs=256 skip=1" % resDir, shell = True)
    seekedCopy(os.path.join(resDir, "7.1.1_volantis_n9f27m", "boot.img"), "boot.img", 256)
    verifySingleJson(resDir + "/7.1.1_volantis_n9f27m/boot.json")
    # 7.0 special recovery
    cleanUp()
    #subprocess.check_call("dd if=%s/7.1.1_volantis_n9f27m/recovery.img of=recovery.img bs=256 skip=1" % resDir, shell = True)
    seekedCopy(os.path.join(resDir, "7.1.1_volantis_n9f27m", "recovery.img"), "recovery.img", 256)
    verifySingleJson("%s/7.1.1_volantis_n9f27m/recovery.json" % resDir)
    # 8.0
    verifySingleDir(resDir, "8.0.0_fugu_opr2.170623.027")
    # 9.0 + avb
    verifySingleDir(resDir, "9.0.0_blueline_pq1a.181105.017.a1")
    # Q preview
    verifySingleDir(resDir, "Q_preview_blueline_qpp2.190228.023")
    # 10
    verifySingleDir(resDir, "10.0.0_coral-qq1d.200205.002")
    # 11
    verifySingleDir(resDir, "11.0.0_redfin.rd1a.200810.021.a1")

    #########################################
    #   resource_2
    #########################################
    cleanUp()
    verifySingleJson("%s/issue_59/recovery.json" % resDir2, func = lambda: shutil.rmtree("build/unzip_boot/root", ignore_errors = False))
    # Issue 71: dtbo
    if platform.system() != "Darwin":
        verifySingleDir(resDir2, "issue_71")
        verifySingleDir(resDir2, "issue_71/redfin")
    else:
        log.info("dtbo not fully supported on MacOS, skip testing")
    # Issue 83: init_boot
    verifySingleDir(resDir2, "issue_83")
    # Issue 86: vendor_boot with vrt and board name
    verifySingleDir(resDir2, "issue_86")
    # Issue 88: boot image V4 without boot signature,
    #  and Issue 75: allow duplicated entry in cpio
    verifySingleDir(resDir2, "issue_88")
    # Issue 92: unsigned vendor_boot
    verifySingleDir(resDir2, "issue_91_unsigned_vendor_boot")

    log.info(successLogo)

if __name__ == "__main__":
    # execute only if run as a script
    main()
