#!/usr/bin/env python3

import shutil, os.path, json, subprocess, hashlib, glob
import unittest, logging, sys, lzma

successLogo = """
      +----------------------------------+
      |    All test cases have PASSED    |
      +----------------------------------+
"""
resDir = "src/integrationTest/resources"
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
        os.remove(inFile)

def cleanUp():
    log.info("clean up ...")
    shutil.rmtree("build", ignore_errors = True)
    deleteIfExists("boot.img")
    deleteIfExists("boot.img.clear")
    deleteIfExists("boot.img.google")
    deleteIfExists("boot.img.signed")
    deleteIfExists("boot.img.signed2")
    deleteIfExists("recovery.img")
    deleteIfExists("recovery.img.clear")
    deleteIfExists("recovery.img.google")
    deleteIfExists("recovery.img.signed")
    deleteIfExists("recovery.img.signed2")
    deleteIfExists("vbmeta.img")
    deleteIfExists("vbmeta.img.signed")

def verifySingleJson(jsonFile):
    log.info(jsonFile)
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
    subprocess.check_call("gradle unpack", shell = True)
    subprocess.check_call("gradle pack", shell = True)
    for k, v in verifyItems["hash"].items():
        log.info("%s : %s" % (k, v))
        unittest.TestCase().assertEqual(v, hashFile(k))

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
        subprocess.check_call(pyFile, shell = True)
        cleanUp()

def decompressXZ(inFile, outFile):
    with lzma.open(inFile) as f:
        file_content = f.read()
        with open(outFile, "wb") as f2:
            f2.write(file_content)

def main():
    # from volunteers
    verifySingleDir(resDir, "recovery_image_from_s-trace")
    verifySingleDir(resDir, "boot_img_from_gesangtome") # android 9, no ramdisk
    # 5.0
    verifySingleDir(resDir, "5.0_fugu_lrx21m")
    # 6.0
    verifySingleDir(resDir, "6.0.0_bullhead_mda89e")
    # 7.0 special boot
    cleanUp()
    subprocess.check_call("dd if=%s/7.1.1_volantis_n9f27m/boot.img of=boot.img bs=256 skip=1" % resDir, shell = True)
    verifySingleJson("%s/7.1.1_volantis_n9f27m/boot.json" % resDir)
    # 7.0 special recovery
    cleanUp()
    subprocess.check_call("dd if=%s/7.1.1_volantis_n9f27m/recovery.img of=recovery.img bs=256 skip=1" % resDir, shell = True)
    verifySingleJson("%s/7.1.1_volantis_n9f27m/recovery.json" % resDir)
    # 8.0
    verifySingleDir(resDir, "8.0.0_fugu_opr2.170623.027")
    # 9.0 + avb
    verifySingleDir(resDir, "9.0.0_blueline_pq1a.181105.017.a1")
    # Q preview
    verifySingleDir(resDir, "Q_preview_blueline_qpp2.190228.023")
    # 10
    verifySingleDir(resDir, "10.0.0_coral-qq1d.200205.002")

    log.info(successLogo)

if __name__ == "__main__":
    # execute only if run as a script
    main()
