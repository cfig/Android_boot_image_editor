.PHONY: clean nativeLibrary
.DEFAULT_GOAL: nativeLibrary

src_dir := src/avbx/cpp
header_dir := src/avbx/headers
build_dir := build
CFLAGS := @build/tmp/compileAvbxStaticLibraryAvbxCpp/options.txt
LD_FLAGS := -lavb -L ../aosp/libavb/build/libs/avb/shared

CPP_FILES := $(wildcard $(src_dir)/*.cpp)
OBJ_FILES := $(patsubst $(src_dir)/%,build/%,$(CPP_FILES:.cpp=.o))

clean:
	rm -fr $(build_dir)

build/%.o: $(src_dir)/%.cpp
	mkdir -p $(dir $@)
	$(CXX) -fPIC $(CFLAGS) -o $@ $<

build/libavbxShared.so: $(OBJ_FILES)
	$(CXX) -shared -fPIC $^ $(LD_FLAGS) -o $@

build/libavbxStatic.a: $(OBJ_FILES)
	$(warning $(OBJ_FILES))
	ar rcs $@ $^

build/avbxVerifier.o: src/avbVerifier/cpp/main.cpp
	$(CXX) $^ @build/tmp/compileAvbVerifierExecutableAvbVerifierCpp/options.txt -o $@
build/avbxVerifier: build/libavbxStatic.a
build/avbxVerifier: build/avbxVerifier.o
	$(CXX) $< \
		-lavb -L ../aosp/libavb/build/libs/avb/static \
		-lavbxStatic -L build \
		-o $@
