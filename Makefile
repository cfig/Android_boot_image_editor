all:
	-git apply external/remove_projects.diff
	make -C aosp/mkbootfs
	make -C aosp/libsparse/base/
	make -C aosp/libsparse/sparse/
	make -C aosp/libsparse
	make -C aosp/libavb
clean:
	-git apply -R external/remove_projects.diff
	make clean -C aosp/mkbootfs
	make clean -C aosp/libsparse/base/
	make clean -C aosp/libsparse/sparse/
	make clean -C aosp/libsparse
	make clean -C aosp/libavb
