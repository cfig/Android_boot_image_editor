#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
# Copyright © 2023 cfig <yuyezhong@gmail.com>
#

"""

"""

import os, shutil, os.path, logging, sys, subprocess
from zipfile import ZipFile

#########################
##      globals
#########################

log = logging.getLogger("|")
log.setLevel(logging.DEBUG)
consoleHandler = logging.StreamHandler(sys.stdout)
consoleHandler.setFormatter(logging.Formatter(fmt='%(asctime)s %(levelname)-8s %(name)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'))
log.addHandler(consoleHandler)

known_list = [
    "boot.img",
    "dtbo.img",
    "init_boot.img",
    "product.img",
    "system_dlkm.img",
    "system_ext.img",
    "system.img",
    "system_other.img",
    "vbmeta.img",
    "vbmeta_system.img",
    "vbmeta_vendor.img",
    "vendor_boot.img",
    "vendor_dlkm.img",
    "vendor.img",
    "vendor_kernel_boot.img"
    ]

unknown_list = [
    "cmnlib.img", #Pixel3
    "abl.img", #Pixel3
    "aop.img",  #Pixel3
    "cmnlib64.img", #Pixel3
    "devcfg.img", #Pixel3
    "hyp.img", #Pixel3
    "keymaster.img", #Pixel3
    "modem.img", #Pixel3
    "qupfw.img", #Pixel3
    "tz.img", #Pixel3
    "xbl.img", #Pixel3
    "xbl_config.img", #Pixel3
    "pvmfw.img",  #Pixel7
    "super_empty.img"
    ]

tmp2 = "tmp2"

#########################
##      functions
#########################
def purgeFolder(folderPath):
    if os.path.exists(folderPath):
        log.info("cleaning %s" % folderPath)
        shutil.rmtree(folderPath)

def prepare(zipFile):
    tmp1 = "tmp1"
    list1 = []
    list2 = []
    purgeFolder(tmp1)
    purgeFolder(tmp2)
    os.mkdir(tmp1)
    os.mkdir(tmp2)

    with ZipFile(zipFile, 'r') as zf:
        zf.extractall(path=tmp1)

    imgZip = None
    for item1 in os.listdir(tmp1):
        log.info("> %s" % item1)
        for item2 in os.listdir(os.path.join(tmp1, item1)):
            item = os.path.join(tmp1, item1, item2)
            log.info(">> %s" % item)
            if (item2.endswith(".zip")):
                log.info("+ %s" % item)
                if not imgZip:
                    imgZip = item
                else:
                    raise

    log.info(imgZip)

    with ZipFile(imgZip, 'r') as zf:
        zf.extractall(path=tmp2)
    for item1 in os.listdir(tmp2):
        item = os.path.join(tmp2, item1)
        log.info("> %s" % item)
        if item1.endswith(".img"):
            if (item1 in known_list):
                log.info("+ %s" % item1)
                list1.append(item1)
            elif (item1 in unknown_list):
                log.info("- %s" % item1)
                list2.append(item1)
            else:
                raise
    purgeFolder(tmp1)
    return (list1, list2)

def unpackList(workList, outDir):
    purgeFolder(outDir)
    os.mkdir(outDir)
    for index, item in enumerate(workList):
        log.info("%2d/%2d: %s" % (index+1, len(workList), item))
        shutil.copy(os.path.join(tmp2, item), ".")
        subprocess.check_call("gradle unpack", shell = True)
        shutil.copytree("build/unzip_boot",
                os.path.join(outDir, os.path.splitext(item)[0]),
                symlinks = True)
        subprocess.check_call("gradle clear", shell = True)

def main():
    if len(sys.argv) != 2:
        print("Usage: %s <factory_image.zip>")
        sys.exit(1)
    if not sys.argv[1].endswith(".zip"):
        print("Usage: %s <factory_image.zip>")
    factoryImageZip = sys.argv[1]
    workList, ignoreList = prepare(factoryImageZip)
    print("    Images to extract")
    for index, item in enumerate(workList):
        print("%2d/%2d: %s" % (index+1, len(workList), item))
    print("    Ignored Images ")
    for index, item in enumerate(ignoreList):
        print("%2d/%2d: %s" % (index+1, len(ignoreList), item))
    outDir = os.path.splitext(os.path.basename(sys.argv[1]))[0]
    unpackList(workList, outDir)
    purgeFolder(tmp2)
    log.info("SUCCESS: %s is ready" % outDir)

#########################
##      main
#########################

if __name__ == "__main__":
    # execute only if run as a script
    main()
