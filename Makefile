t:
	external/extract_kernel.py \
		--input build/unzip_boot/kernel \
		--output-configs kernel_configs.txt \
		--output-version kernel_version.txt
	
t2:
	rm -fr dtbo
	mkdir dtbo
	external/mkdtboimg.py \
		dump dtbo.img \
		--dtb dtbo/dtb.dump \
		--output dtbo/header.dump
