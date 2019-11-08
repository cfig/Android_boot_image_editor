.PHONY: nativeExecutable clean
osSuffix := linux
nativeExecutable: \
	build/append2simg \
    simg2img/build/install/main/release/$(osSuffix)/simg2img \
    build/simg2simg \
    build/img2simg
CFLAGS := -Isparse/src/main/public
LDFLAGS :=  \
    -Lsparse/build/lib/main/release/linux/ -lsparse \
    -Lbase/build/lib/main/release/linux/ -lbase \
    -lz
build/append2simg: append2simg/src/main/cpp/append2simg.cpp
	mkdir -p $(dir $@)
	$(CXX) $^ $(CFLAGS) $(LDFLAGS) -o $@
simg2img/build/install/main/release/$(osSuffix)/simg2img: simg2img/src/main/cpp/simg2img.cpp
	mkdir -p $(dir $@)
	$(CXX) $^ $(CFLAGS) $(LDFLAGS) -o $@
build/img2simg: img2simg/src/main/cpp/img2simg.cpp
	mkdir -p $(dir $@)
	$(CXX) $^ $(CFLAGS) $(LDFLAGS) -o $@
build/simg2simg: simg2simg/src/main/cpp/simg2simg.cpp
	mkdir -p $(dir $@)
	$(CXX) $^ $(CFLAGS) $(LDFLAGS) -o $@
clean:
	rm -fr build
	rm -fr simg2img/build/
