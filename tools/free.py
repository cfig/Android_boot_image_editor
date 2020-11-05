#! /usr/bin/env python3
# -*- coding: utf-8 -*-
# vim:fenc=utf-8
#
import subprocess

def run(cmd):
    print(cmd)
    subprocess.check_call(cmd, shell = True)

run("touch vbmeta.img")
run("gradle pull")
run("gradle unpack")
run('vim -u NONE -N build/unzip_boot/vbmeta.avb.json  -c ":19s/0/2/g" -c ":wq"')
run("gradle pack")
run("gradle flash")

