# Android_boot_image_editor
[![Build Status](https://travis-ci.org/cfig/Android_boot_image_editor.svg?branch=master)](https://travis-ci.org/cfig/Android_boot_image_editor)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Gitter chat](https://badges.gitter.im/gitterHQ/gitter.png)](https://gitter.im/Android_boot_image_editor/lobby)

This tool focuses on editing Android boot.img(also recovery.img, recovery-two-step.img and vbmeta.img).

## 1. Prerequisite
#### 1.1 Host OS requirement:

Linux or Mac.
Also need python 2.x and jdk 8.

#### 1.2 Target Android requirement:

(1) Target boot.img MUST follows AOSP verified boot flow, either [Boot image signature](https://source.android.com/security/verifiedboot/verified-boot#signature_format) in VBoot 1.0 or [AVB HASH footer](https://android.googlesource.com/platform/external/avb/+/master/README.md#The-VBMeta-struct) in VBoot 2.0.

Supported images:
 - boot.img
 - recovery.img
 - recovery-two-step.img
 - vbmeta.img

(2) These utilities are known to work for Nexus/Pixel boot.img for the following Android releases:

 - AOSP master
 - Lollipop (5.0) - Pie (9)

## 2. Usage
Put your boot.img to current directory, then start gradle 'unpack' task:

    cp <original_boot_image> boot.img
    ./gradlew unpack

Your get the flattened kernel and /root filesystem under **./build/unzip\_boot**:

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


## 3. example & test
An example boot.img has been placed at **src/test/resources/boot.img**, which is extracted from Nexus 5x(code: bullhead) factory images from [Google](https://dl.google.com/dl/android/aosp/bullhead-mda89e-factory-29247942.tgz), you can take it as a quick start.

## 4. boot.img layout
Read [layout](doc/layout.md) of Android boot.img.
We now support both VB 1.0 and AVB 2.0 layouts.

## 5. compatible devices

| Device Model                   | Manufacturer | Compatible           | Android Version          | Note |
|--------------------------------|--------------|----------------------|--------------------------|------|
| Pixel XL (marlin)              | HTC          | Y                    | 9.0.0 (PPR2.180905.006, <Br>Sep 2018)| [more ...](doc/additional_tricks.md#pixel-xl-marlin) |
| Z18(NX606J)                    | ZTE          | Y                    | 8.1.0                    | [more...](doc/additional_tricks.md#nx606j) |
| Nexus 9 (volantis/flounder)    | HTC          | Y(with some tricks)  | 7.1.1 (N9F27M, Oct 2017) | [tricks](doc/additional_tricks.md#tricks-for-nexus-9volantis)|
| Nexus 5x (bullhead)            | LG           | Y                    | 6.0.0_r12 (MDA89E)       |      |
| Moto X (2013) T-Mobile         | Motorola     | N                    |                          |      |

## 6. References

boot\_signer
https://android.googlesource.com/platform/system/extras

bouncycastle
https://android.googlesource.com/platform/external/bouncycastle

cpio / fs\_config
https://android.googlesource.com/platform/system/core

AVB
https://android.googlesource.com/platform/external/avb/

Android version list
https://source.android.com/source/build-numbers.html
