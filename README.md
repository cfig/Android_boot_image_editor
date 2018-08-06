# Android_boot_image_editor
[![Build Status](https://travis-ci.org/cfig/Android_boot_image_editor.svg?branch=master)](https://travis-ci.org/cfig/Android_boot_image_editor)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

This tool focuses on editing Android boot.img(also recovery.img, recovery-two-step.img and vbmeta.img).

## Prerequisite
#### Host OS requirement:

Linux or Mac.
Also need python 2.x(required by avbtool) and java 8.

#### Target Android requirement:

(1) Target boot.img(or recovery.img / recovery-two-step.img) MUST follows AOSP verified boot flow, either [Boot image signature](https://source.android.com/security/verifiedboot/verified-boot#signature_format) in VBoot 1.0 or [AVB HASH footer](https://android.googlesource.com/platform/external/avb/+/master/README.md#The-VBMeta-struct) in VBoot 2.0.

(2) These utilities are known to work for Nexus/Pixel boot.img(or recovery.img/recovery-two-step.img/vbmeta.img) for the following Android releases:

 - AOSP master
 - Lollipop (5.0) - Pi (9)

You can get a full [Android version list](https://source.android.com/source/build-numbers.html) here.

## Usage
Get tools via git:

    git clone https://github.com/cfig/Android_boot_image_editor.git
    cd Android_boot_image_editor

Then put your boot.img at **$(CURDIR)/boot.img**, then start gradle 'unpack' task:

    cp <original_boot_image> boot.img
    ./gradlew unpack

Your get the flattened kernel and /root filesystem under **$(CURDIR)/build/unzip\_boot**:

    build/unzip_boot/
    ├── boot.img.avb.json (AVB only)
    ├── bootimg.json (boot image info)
    ├── kernel
    ├── second (2nd bootloader, if exists)
    └── root

Then you can edit the actual file contents, like rootfs or kernel.
Now, pack the boot.img again

    ./gradlew pack

You get the repacked boot.img at $(CURDIR):

    boot.img.signed

#### If you are working with recovery.img
If you are working with recovery.img, the steps are similar:

    cp <original_recovery_image> recovery.img
    ./gradlew unpack
    ./gradlew pack

And you get recovery.img.signed


## example & test
An example boot.img has been placed at **src/test/resources/boot.img**, which is extracted from Nexus 5x(code: bullhead) factory images from [Google](https://dl.google.com/dl/android/aosp/bullhead-mda89e-factory-29247942.tgz), you can take it as a quick start.

## boot.img layout
Read [layout](README.expert.md) of Android boot.img.
We now support both VB 1.0 and AVB 2.0 layouts.

## References

boot\_signer
https://android.googlesource.com/platform/system/extras

bouncycastle
https://android.googlesource.com/platform/external/bouncycastle

cpio / fs\_config
https://android.googlesource.com/platform/system/core

AVB
https://android.googlesource.com/platform/external/avb/
