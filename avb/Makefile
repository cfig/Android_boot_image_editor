t:
	cp boot.img boot.img.orig
	./avbtool add_hash_footer \
		--image boot.img \
		--partition_size 33554432 \
		--partition_name boot \
		--algorithm SHA256_RSA4096 \
		--key avb_test_data/testkey_rsa4096.pem
	./avbtool info_image --image boot.img
	./avbtool erase_footer --image boot.img
	-./avbtool info_image --image boot.img
