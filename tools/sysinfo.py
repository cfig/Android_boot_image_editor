#! /usr/bin/env python
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
import os, sys, subprocess, gzip, logging, shutil, tarfile, os.path

#########################
##      globals
#########################
log = logging.getLogger("|")
log.setLevel(logging.DEBUG)
consoleHandler = logging.StreamHandler(sys.stdout)
consoleHandler.setFormatter(logging.Formatter(fmt='%(asctime)s %(levelname)-8s %(name)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'))
log.addHandler(consoleHandler)

#########################
##      functions
#########################
def purgeFolder(folderPath):
    if os.path.exists(folderPath):
        log.info("cleaning %s" % folderPath)
        shutil.rmtree(folderPath)

def makeTar(output_filename, source_dir):
    with tarfile.open(output_filename, "w:xz") as tar:
        tar.add(source_dir, arcname=os.path.basename(source_dir))

#########################
##      main
#########################
log.info("adb wait-for-device ...")
subprocess.check_call("adb wait-for-device", shell = True)
subprocess.check_call("adb root", shell = True)
purgeFolder("sysinfo")
os.mkdir("sysinfo")
with open("sysinfo/0_prop", "wb") as f:
    f.write(subprocess.check_output("adb shell getprop", shell = True))
with open("sysinfo/1_partitions", "wb") as f:
    f.write(subprocess.check_output("adb shell cat /proc/partitions", shell = True))
    f.write(subprocess.check_output("adb shell ls -l /dev/block/by-name", shell = True))
with open("sysinfo/2_mount", "wb") as f:
    f.write(subprocess.check_output("adb shell mount", shell = True))
with open("sysinfo/3_kernel_cmdline", "wb") as f:
    f.write(bytes("[version]\n", "utf-8"))
    f.write(subprocess.check_output("adb shell cat /proc/version", shell = True))
    f.write(bytes("\n[cmdline]\n", "utf-8"))
    f.write(subprocess.check_output("adb shell cat /proc/cmdline", shell = True))
    f.write(bytes("\n[bootconfig]\n", "utf-8"))
    try:
        f.write(subprocess.check_output("adb shell cat /proc/bootconfig", shell = True))
    except subprocess.CalledProcessError as e:
        log.warning("can not read bootconfig")
        pass
    subprocess.check_call("adb pull /proc/config.gz", shell = True)
    with gzip.open("config.gz", "rb") as gz_file:
        file_content = gz_file.read()
        f.write(bytes("\n[defconfig]\n", "utf-8"))
        f.write(file_content)
    os.remove("config.gz")
subprocess.check_call("adb pull /proc/device-tree", cwd = "sysinfo", shell = True)
shutil.move("sysinfo/device-tree", "sysinfo/device_tree")
makeTar("sysinfo.py.tar.xz", "sysinfo")
shutil.rmtree("sysinfo")
log.info("sysinfo.py.tar.xz is ready")
