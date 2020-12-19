#
# release.mk
# yu, 2020-12-20 00:19
#

define gw
#!/usr/bin/env sh\n
if [ "x$$1" = "xassemble" ]; then\n
    echo "already assembled"\n
    exit\n
fi\n
if [ "x$$1" = "xcheck" ]; then\n
echo "no check is needed"\n
exit 0\n
fi\n
if [ "x$$1" = "xclean" ]; then\n
echo "no cleaning is needed"\n
exit 0\n
fi\n
java -jar bbootimg/bbootimg.jar $$*

endef
export gw
all:
	cd ../bbootimg && gradle build
	cp ../bbootimg/build/libs/bbootimg.jar .
	cd ../aosp/boot_signer && gradle build
	cp ../aosp/boot_signer/build/libs/boot_signer.jar .
	cd .. && rm -fr avbImpl  bbootimg build build.gradle.kts gradle gradlew gradlew.bat settings.gradle.kts
	cd ../aosp && rm -r libavb1.1 libavb1.2 mkbootfs.10 mkbootfs.11
	rm -r ../aosp/boot_signer
	mkdir -p ../aosp/boot_signer/build/libs/ && mv boot_signer.jar ../aosp/boot_signer/build/libs/
	mkdir ../bbootimg && mv bbootimg.jar ../bbootimg/
	echo $$gw > gradlew
	chmod 755 gradlew
	mv gradlew ../

# vim:ft=make
#
