src_dir := src/mkbootfs/cpp
header_dir := src/mkbootfs/headers
build_dir := build
CXX_FLAGS := -I$(header_dir) -std=gnu++11 -Wno-write-strings -D__ANDROID_VNDK__

.PHONY: clean nativeExecutable
nativeExecutable: $(build_dir)/exe/mkbootfs/mkbootfs
$(build_dir)/exe/mkbootfs/mkbootfs: $(build_dir)/fs_config.o $(build_dir)/mkbootfs.o |$(build_dir)/exe/mkbootfs
	$(CXX) $^ -o $@
$(build_dir)/fs_config.o: $(src_dir)/fs_config.cpp |$(build_dir)
	$(CXX) -c $(CXX_FLAGS) $< -o $@
$(build_dir)/mkbootfs.o: $(src_dir)/mkbootfs.c |$(build_dir)
	$(CXX) -c $(CXX_FLAGS) $< -o $@
$(build_dir):
	mkdir $@
$(build_dir)/exe/mkbootfs: |$(build_dir)
	mkdir -p $@
clean:
	rm -rf $(build_dir)
