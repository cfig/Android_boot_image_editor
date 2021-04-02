.PHONY: all checkpolicy libsepol prepare
nothing:
	@echo "Nothing to do"

checkpolicy: export CFLAGS := -g -Wall -Werror -Wshadow -pipe -fno-strict-aliasing -I$(CURDIR)/libsepol-3.2/include
checkpolicy: export LIBSEPOLA := $(CURDIR)/libsepol-3.2/src/libsepol.a
checkpolicy: export LDLIBS_LIBSEPOLA := -l:libsepol.a -L$(CURDIR)/libsepol-3.2/src
checkpolicy: libsepol
	make -C checkpolicy-3.2 -j
	cp checkpolicy-3.2/test/dispol .

libsepol:
	make -C libsepol-3.2 -j

prepare:
	rm -fr libsepol-3.2 checkpolicy-3.2
	wget https://github.com/SELinuxProject/selinux/releases/download/3.2/libsepol-3.2.tar.gz
	wget https://github.com/SELinuxProject/selinux/releases/download/3.2/checkpolicy-3.2.tar.gz
	tar xaf checkpolicy-3.2.tar.gz
	tar xaf libsepol-3.2.tar.gz

all: checkpolicy libsepol prepare
# vim:ft=make
#
