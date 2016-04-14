.DEFAULT_GOAL := flat

SHELL := /bin/bash
WORK_DIR := unzip_boot

help:
	@echo "flat        : boot.subimg -> unzip_boot/*"
	@echo "boot.img    : unsigned boot image"
	@echo "boot.subimg : signed boot image"
	@echo "addon       : (recovery only) add additional tools"

.PHONY: flat
flat:
	rm -fr $(WORK_DIR)
	mkdir -p $(WORK_DIR)/root
	abootimg -x boot.subimg $(WORK_DIR)/bootimg.cfg $(this_kernel) $(this_ramdisk).gz
	gzip -c -d $(this_ramdisk).gz > $(this_ramdisk)
	rm $(this_ramdisk).gz
	cd $(WORK_DIR)/root && \
		cpio -i -F ../ramdisk.img
	@rm $(WORK_DIR)/ramdisk.img
	@echo && echo "===================================" && file $(WORK_DIR)/* && echo "==================================="
kernel_cmdline := "$(shell grep -Po '(?<=cmdline = ).*' $(WORK_DIR)/bootimg.cfg)"
this_root := $(WORK_DIR)/root
this_kernel := $(WORK_DIR)/kernel
this_ramdisk := $(WORK_DIR)/ramdisk.img
ifeq '$(TARGET_PRODUCT)' ''
$(warning NON-android)
this_verity_key := tools/security/verity
else
$(warning android)
this_verity_key := build/target/product/security/verity
endif

.INTERMEDIATE: $(this_ramdisk).gz boot.img
$(this_ramdisk).gz: $(this_root)
	mkbootfs $< | gzip > $@
boot.img: $(this_ramdisk).gz $(this_kernel)
	mkbootimg \
		--kernel $(this_kernel) \
		--ramdisk $(this_ramdisk).gz \
		--cmdline "$(shell echo $(kernel_cmdline))" \
		--base 0x01000000 \
		--output $@
boot.subimg: boot.img
	$(call signer,/boot,$<,$@)

define signer
	boot_signer $(1) $(2) $(this_verity_key).pk8 $(this_verity_key).x509.pem $(3)
endef

mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
real_mkfile_path := $(shell readlink $(mkfile_path))

libs := libc.so libcrypto.so libcutils.so libm.so libselinux.so libstdc++.so libpcre.so liblog.so libnetutils.so libsysutils.so libutils.so libbacktrace.so libstlport.so libgccdemangle.so libunwind.so libunwind-ptrace.so
bins := toolbox sh linker netcfg logd logcat
addon: | unzip_boot/root/system/bin
addon: | unzip_boot/root/system/lib
addon: INITRC := unzip_boot/root/init.recovery.marvellberlin.rc
addon:
	#initrc
	echo "service console /system/bin/sh" > $(INITRC)
	echo "    console" >> $(INITRC)
	echo "    user root" >> $(INITRC)
	echo "    group root" >> $(INITRC)
	echo >> $(INITRC)
	echo "service logd /system/bin/logd" >> $(INITRC)
	echo "    socket logd stream 0666 logd logd" >> $(INITRC)
	echo "    socket logdr seqpacket 0666 logd logd" >> $(INITRC)
	echo "    socket logdw dgram 0222 logd logd" >> $(INITRC)
	echo "    seclabel u:r:logd:s0" >> $(INITRC)
	#recovery
	#cp out/target/product/$(TARGET_PRODUCT)/system/bin/recovery unzip_boot/root/sbin/
	#@cp -v out/target/product/$(TARGET_PRODUCT)/obj/EXECUTABLES/recovery_intermediates/recovery unzip_boot/root/sbin/
	#bin
	@$(foreach item,$(bins), \
	  cp -v out/target/product/$(TARGET_PRODUCT)/system/bin/$(item)   unzip_boot/root/system/bin/; $(newline))
	#lib
	@$(foreach item,$(libs), \
	  cp -v out/target/product/$(TARGET_PRODUCT)/system/lib/$(item)   unzip_boot/root/system/lib/; $(newline))
	#@cp -v out/target/product/$(TARGET_PRODUCT)/system/etc/sepolicy.recovery unzip_boot/root/sepolicy
	@cp -v out/target/product/$(TARGET_PRODUCT)/obj/ETC/sepolicy.recovery_intermediates/sepolicy.recovery unzip_boot/root/sepolicy


unzip_boot/root/system/bin:
	mkdir $@
unzip_boot/root/system/lib:
	mkdir $@

#service console /system/bin/sh
#    console
#    user root
#    group root
