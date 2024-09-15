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
run("touch boot.img")
run("gradle pull")
run("gradle unpack")

