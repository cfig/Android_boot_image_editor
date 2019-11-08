.PHONY: clean nativeLibrary
.DEFAULT_GOAL: nativeLibrary
src_dir := src/avb/c
header_dir := src/avb/headers
build_dir := build
CFLAGS := \
    -I$(header_dir) \
    -D_FILE_OFFSET_BITS=64 \
    -D_POSIX_C_SOURCE=199309L \
    -DAVB_ENABLE_DEBUG \
    -DAVB_COMPILATION \
    -Wall -g
nativeLibrary: build/libs/avb/shared/libavb.so
CPP_FILES := $(wildcard src/avb/c/*.c)
OBJ_FILES := $(patsubst src/avb/c/%,build/%,$(CPP_FILES:.c=.o))

clean:
	rm -fr $(build_dir)

build/%.o: $(src_dir)/%.c
	mkdir -p $(dir $@)
	$(CC) -fPIC $(CFLAGS) -c -o $@ $<

build/libs/avb/shared/libavb.so: $(OBJ_FILES)
	mkdir -p $(dir $@)
	$(CC) -shared -fPIC $^ -o $@
