#
# release.mk
# yuyezhong@gmail.com, 2020-12-20 00:19
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
\n
if [ "$$(echo "$$2" | grep -E '^\-Dpart=')" ]; then\n
 set -x\n
 java "$$2" -jar bbootimg/bbootimg.jar "$$1"\n
else\n
 set -x\n
 java -jar bbootimg/bbootimg.jar "$$@"\n
fi\n

endef

define gw_win
@IF EXIST tools\\bin SET PATH=%PATH%;tools\\bin\n
@if "%1" == "check" exit 0\n
@if "%1" == "clean" exit 0\n
@java -jar bbootimg/bbootimg.jar %*
endef
export gw gw_win
all:
	cd ../bbootimg && gradle build
	cd ../ && gradle aosp:apksigner:build
	cp ../bbootimg/build/libs/bbootimg.jar .
	cd ../aosp/boot_signer && gradle build
	cp ../aosp/boot_signer/build/libs/boot_signer.jar .
	cp ../aosp/apksigner/build/libs/apksigner-1.0.jar .
	cd .. && rm -fr avbImpl  bbootimg build build.gradle.kts gradle gradlew gradlew.bat settings.gradle.kts
	cd ../aosp && rm -r libavb1.1 libavb1.2 mkbootfs.10 mkbootfs.11
	rm -r ../aosp/boot_signer
	rm -fr ../helper
	rm -fr ../lazybox
	rm -fr ../aosp/apksigner
	rm -fr ../aosp/bouncycastle
	mkdir -p ../aosp/boot_signer/build/libs/ && mv -v boot_signer.jar ../aosp/boot_signer/build/libs/
	mkdir -p ../aosp/apksigner/build/libs/ && mv -v apksigner-1.0.jar ../aosp/apksigner/build/libs/
	mkdir ../bbootimg && mv bbootimg.jar ../bbootimg/
	echo $$gw > gradlew
	chmod 755 gradlew
	echo $$gw_win > gradlew.bat
	mv gradlew ../
	mv gradlew.bat ../

# vim:ft=make
#
