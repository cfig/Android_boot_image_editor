.PHONY: clean nativeLibrary
.DEFAULT_GOAL: nativeLibrary
src_dir := src/main/cpp
header_dir := src/main/public
build_dir := build
CFLAGS :=
nativeLibrary: build/lib/main/release/linux/libbase.a
CPP_FILES := $(wildcard $(src_dir)/*.cpp)
OBJ_FILES := $(patsubst $(src_dir)/%,build/%,$(CPP_FILES:.cpp=.o))

clean:
	rm -fr $(build_dir)

build/%.o: $(src_dir)/%.cpp
	mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c -o $@ $<

build/lib/main/release/linux/libbase.a: $(OBJ_FILES)
	mkdir -p $(dir $@)
	ar rcs $@ $^
