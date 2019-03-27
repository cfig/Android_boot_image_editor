#!/usr/bin/env python3

import shutil, os.path, json, subprocess, hashlib, glob
import unittest

def hashFile(fileName):
    hasher = hashlib.md5()
    with open(fileName, 'rb') as afile:
        buf = afile.read()
        hasher.update(buf)
    return hasher.hexdigest()

def deleteIfExists(inFile):
    if os.path.isfile(inFile):
        os.remove(inFile)

def verifySingleJson(inResourceDir, inImageDir, jsonFile):
    print(jsonFile)
    resDir = inResourceDir
    imgDir = inImageDir
    verifyItems = json.load(open(jsonFile))
    for k, v in verifyItems["copy"].items():
        shutil.copyfile(os.path.join(resDir, imgDir, k), v)

    subprocess.check_call("gradle unpack", shell = True)
    subprocess.check_call("gradle pack", shell = True)

    for k, v in verifyItems["hash"].items():
        print("%s : %s" % (k, v))
        unittest.TestCase().assertEqual(hashFile(k), v)

    shutil.rmtree("build")
    deleteIfExists("boot.img")
    deleteIfExists("boot.img.clear")
    deleteIfExists("boot.img.google")
    deleteIfExists("boot.img.signed")
    deleteIfExists("recovery.img")
    deleteIfExists("recovery.img.clear")
    deleteIfExists("recovery.img.google")
    deleteIfExists("recovery.img.signed")
    deleteIfExists("vbmeta.img")
    deleteIfExists("vbmeta.img.signed")

def verifySingleDir(inResourceDir, inImageDir):
    resDir = inResourceDir
    imgDir = inImageDir
    print("enter %s ..." % os.path.join(resDir, imgDir))
    jsonFiles = glob.glob(os.path.join(resDir, imgDir) + "/*.json")
    for jsonFile in jsonFiles:
        verifySingleJson(inResourceDir, inImageDir, jsonFile)

# 5.0
verifySingleDir("boot_image_res", "5.0_fugu_lrx21m")
# 6.0
verifySingleDir("boot_image_res", "6.0.0_bullhead_mda89e")
# 7.0 special boot
subprocess.check_call("dd if=boot_image_res/7.1.1_volantis_n9f27m/boot.img of=boot.img bs=256 skip=1", shell = True)
verifySingleJson("boot_image_res", "7.1.1_volantis_n9f27m", "boot_image_res/7.1.1_volantis_n9f27m/boot.json")
# 7.0 special recovery
subprocess.check_call("dd if=boot_image_res/7.1.1_volantis_n9f27m/recovery.img of=recovery.img bs=256 skip=1", shell = True)
verifySingleJson("boot_image_res", "7.1.1_volantis_n9f27m", "boot_image_res/7.1.1_volantis_n9f27m/recovery.json")
# 8.0
verifySingleDir("boot_image_res", "8.0.0_fugu_opr2.170623.027")
# 9.0 + avb
subprocess.check_call("tar xf boot_image_res/9.0.0_blueline_pq1a.181105.017.a1/boot.img.tar.gz", shell = True)
verifySingleJson("boot_image_res", "9.0.0_blueline_pq1a.181105.017.a1", "boot_image_res/9.0.0_blueline_pq1a.181105.017.a1/boot.json")
verifySingleJson("boot_image_res", "9.0.0_blueline_pq1a.181105.017.a1", "boot_image_res/9.0.0_blueline_pq1a.181105.017.a1/vbmeta.json")
