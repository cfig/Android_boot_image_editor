#!/usr/bin/env python3
#
# Copyright 2022, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Tests certify_bootimg."""

import logging
import glob
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest

BOOT_SIGNATURE_SIZE = 16 * 1024

TEST_KERNEL_CMDLINE = (
    'printk.devkmsg=on firmware_class.path=/vendor/etc/ init=/init '
    'kfence.sample_interval=500 loop.max_part=7 bootconfig'
)


def generate_test_file(pathname, size, seed=None):
    """Generates a gibberish-filled test file and returns its pathname."""
    random.seed(os.path.basename(pathname) if seed is None else seed)
    with open(pathname, 'wb') as file:
        file.write(random.randbytes(size))
    return pathname


def generate_test_boot_image(boot_img, kernel_size=4096, seed='kernel',
                             avb_partition_size=None):
    """Generates a test boot.img without a ramdisk."""
    with tempfile.NamedTemporaryFile() as kernel_tmpfile:
        generate_test_file(kernel_tmpfile.name, kernel_size, seed)
        kernel_tmpfile.flush()

        mkbootimg_cmds = [
            'mkbootimg',
            '--header_version', '4',
            '--kernel', kernel_tmpfile.name,
            '--cmdline', TEST_KERNEL_CMDLINE,
            '--os_version', '12.0.0',
            '--os_patch_level', '2022-03',
            '--output', boot_img,
        ]
        subprocess.check_call(mkbootimg_cmds)

    if avb_partition_size:
        avbtool_cmd = ['avbtool', 'add_hash_footer', '--image', boot_img,
                       '--partition_name', 'boot',
                       '--partition_size', str(avb_partition_size)]
        subprocess.check_call(avbtool_cmd)


def generate_test_boot_image_archive(archive_file_name, archive_format,
                                     boot_img_info, gki_info=None):
    """Generates an archive of test boot images.

    It also adds a file gki-info.txt, which contains additional settings for
    for `certify_bootimg --extra_args`.

    Args:
        archive_file_name: the name of the archive file to create, including the
          path, minus any format-specific extension.
        archive_format: the |format| parameter for shutil.make_archive().
          e.g., 'zip', 'tar', or 'gztar', etc.
        boot_img_info: a list of (boot_image_name, kernel_size,
          partition_size) tuples. e.g.,
          [('boot.img', 4096, 4 * 1024),
           ('boot-lz4.img', 8192, 8 * 1024)].
        gki_info: the file content to be written into 'gki-info.txt' in the
          created archive.

    Returns:
        The full path of the created archive. e.g., /path/to/boot-img.tar.gz.
    """
    with tempfile.TemporaryDirectory() as temp_out_dir:
        for name, kernel_size, partition_size in boot_img_info:
            boot_img = os.path.join(temp_out_dir, name)
            generate_test_boot_image(boot_img=boot_img,
                                     kernel_size=kernel_size,
                                     seed=name,
                                     avb_partition_size=partition_size)

        if gki_info:
            gki_info_path = os.path.join(temp_out_dir, 'gki-info.txt')
            with open(gki_info_path, 'w', encoding='utf-8') as f:
                f.write(gki_info)

        return shutil.make_archive(archive_file_name,
                                   archive_format,
                                   temp_out_dir)


def has_avb_footer(image):
    """Returns true if the image has a avb footer."""

    avbtool_info_cmd = ['avbtool', 'info_image', '--image', image]
    result = subprocess.run(avbtool_info_cmd, check=False,
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL)

    return result.returncode == 0


def get_vbmeta_size(vbmeta_bytes):
    """Returns the total size of a AvbVBMeta image."""

    # Keep in sync with |AvbVBMetaImageHeader|.
    AVB_MAGIC = b'AVB0'                        # pylint: disable=C0103
    AVB_VBMETA_IMAGE_HEADER_SIZE = 256         # pylint: disable=C0103
    FORMAT_STRING = (                          # pylint: disable=C0103
        '!4s2L'      # magic, 2 x version.
        '2Q'         # 2 x block size: Authentication and Auxiliary blocks.
    )

    if len(vbmeta_bytes) < struct.calcsize(FORMAT_STRING):
        return 0

    data = vbmeta_bytes[:struct.calcsize(FORMAT_STRING)]
    (magic, _, _,
     authentication_block_size,
     auxiliary_data_block_size) = struct.unpack(FORMAT_STRING, data)

    if magic == AVB_MAGIC:
        return (AVB_VBMETA_IMAGE_HEADER_SIZE +
                authentication_block_size +
                auxiliary_data_block_size)
    return 0


def extract_boot_signatures(boot_img, output_dir):
    """Extracts the boot signatures of a boot image.

    This functions extracts the boot signatures of |boot_img| as:
      - |output_dir|/boot_signature1
      - |output_dir|/boot_signature2
    """

    boot_img_copy = os.path.join(output_dir, 'boot_image_copy')
    shutil.copy2(boot_img, boot_img_copy)
    avbtool_cmd = ['avbtool', 'erase_footer', '--image', boot_img_copy]
    subprocess.run(avbtool_cmd, check=False, stderr=subprocess.DEVNULL)

    # The boot signature is assumed to be at the end of boot image, after
    # the AVB footer is erased.
    with open(boot_img_copy, 'rb') as image:
        image.seek(-BOOT_SIGNATURE_SIZE, os.SEEK_END)
        boot_signature_bytes = image.read(BOOT_SIGNATURE_SIZE)
        assert len(boot_signature_bytes) == BOOT_SIGNATURE_SIZE
    os.unlink(boot_img_copy)

    num_signatures = 0
    while True:
        next_signature_size = get_vbmeta_size(boot_signature_bytes)
        if next_signature_size <= 0:
            break

        num_signatures += 1
        next_signature = boot_signature_bytes[:next_signature_size]
        output_path = os.path.join(
            output_dir, 'boot_signature' + str(num_signatures))
        with open(output_path, 'wb') as output:
            output.write(next_signature)

        # Moves to the next signature.
        boot_signature_bytes = boot_signature_bytes[next_signature_size:]


def extract_boot_archive_with_signatures(boot_img_archive, output_dir):
    """Extracts boot images and signatures of a boot images archive.

    Suppose there are two boot images in |boot_img_archive|: boot.img
    and boot-lz4.img. This function then extracts each boot*.img and
    their signatures as:
      - |output_dir|/boot.img
      - |output_dir|/boot-lz4.img
      - |output_dir|/boot/boot_signature1
      - |output_dir|/boot/boot_signature2
      - |output_dir|/boot-lz4/boot_signature1
      - |output_dir|/boot-lz4/boot_signature2
    """
    shutil.unpack_archive(boot_img_archive, output_dir)
    for boot_img in glob.glob(os.path.join(output_dir, 'boot*.img')):
        img_name = os.path.splitext(os.path.basename(boot_img))[0]
        signature_output_dir = os.path.join(output_dir, img_name)
        os.mkdir(signature_output_dir, 0o777)
        extract_boot_signatures(boot_img, signature_output_dir)


class CertifyBootimgTest(unittest.TestCase):
    """Tests the functionalities of certify_bootimg."""

    def setUp(self):
        # Saves the test executable directory so that relative path references
        # to test dependencies don't rely on being manually run from the
        # executable directory.
        # With this, we can just open "./testdata/testkey_rsa2048.pem" in the
        # following tests with subprocess.run(..., cwd=self._exec_dir, ...).
        self._exec_dir = os.path.abspath(os.path.dirname(sys.argv[0]))

        # Set self.maxDiff to None to see full diff in assertion.
        # C0103: invalid-name for maxDiff.
        self.maxDiff = None  # pylint: disable=C0103

        self._EXPECTED_BOOT_SIGNATURE_RSA2048 = (       # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     320 bytes\n'
            'Auxiliary Block:          832 bytes\n'
            'Public key (sha1):        '
            'cdbb77177f731920bbe0a0f94f84d9038ae0617d\n'
            'Algorithm:                SHA256_RSA2048\n'
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            8192 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        boot\n'           # boot
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            'faf1da72a4fba97ddab0b8f7a410db86'
            '8fb72392a66d1440ff8bff490c73c771\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
        )

        self._EXPECTED_KERNEL_SIGNATURE_RSA2048 = (     # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     320 bytes\n'
            'Auxiliary Block:          832 bytes\n'
            'Public key (sha1):        '
            'cdbb77177f731920bbe0a0f94f84d9038ae0617d\n'
            'Algorithm:                SHA256_RSA2048\n'
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            4096 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        generic_kernel\n' # generic_kernel
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '762c877f3af0d50a4a4fbc1385d5c7ce'
            '52a1288db74b33b72217d93db6f2909f\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_SIGNATURE_RSA4096 = (       # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1344 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            8192 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        boot\n'           # boot
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            'faf1da72a4fba97ddab0b8f7a410db86'
            '8fb72392a66d1440ff8bff490c73c771\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
        )

        self._EXPECTED_KERNEL_SIGNATURE_RSA4096 = (     # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1344 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            4096 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        generic_kernel\n' # generic_kernel
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '762c877f3af0d50a4a4fbc1385d5c7ce'
            '52a1288db74b33b72217d93db6f2909f\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_SIGNATURE_WITH_GKI_INFO = (  # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1600 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n' # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            8192 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        boot\n'        # boot
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            'faf1da72a4fba97ddab0b8f7a410db86'
            '8fb72392a66d1440ff8bff490c73c771\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
            "    Prop: KERNEL_RELEASE -> '5.10.42-android13-0-00544-"
            "ged21d463f856'\n"
            "    Prop: BRANCH -> 'android13-5.10-2022-05'\n"
            "    Prop: BUILD_NUMBER -> 'ab8295296'\n"
            "    Prop: GKI_INFO -> 'added here'\n"
        )

        self._EXPECTED_KERNEL_SIGNATURE_WITH_GKI_INFO = (# pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1600 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n' # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            4096 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        generic_kernel\n' # generic_kernel
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '762c877f3af0d50a4a4fbc1385d5c7ce'
            '52a1288db74b33b72217d93db6f2909f\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
            "    Prop: KERNEL_RELEASE -> '5.10.42-android13-0-00544-"
            "ged21d463f856'\n"
            "    Prop: BRANCH -> 'android13-5.10-2022-05'\n"
            "    Prop: BUILD_NUMBER -> 'ab8295296'\n"
            "    Prop: GKI_INFO -> 'added here'\n"
        )

        self._EXPECTED_BOOT_SIGNATURE1_RSA4096 = (       # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1600 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            12288 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        boot\n'           # boot
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '30208b4d0a6d16db47fc13c9527bfe81'
            'a168d3b3940325d1ca8d3439792bfe18\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
            "    Prop: KERNEL_RELEASE -> '5.10.42-android13-0-00544-"
            "ged21d463f856'\n"
            "    Prop: BRANCH -> 'android13-5.10-2022-05'\n"
            "    Prop: BUILD_NUMBER -> 'ab8295296'\n"
            "    Prop: SPACE -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_SIGNATURE2_RSA4096 = (       # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1600 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            8192 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        generic_kernel\n' # generic_kernel
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            'd4c8847e7d9900a98f77e1f0b5272854'
            '7bf9c1e428fea500d419275f72ec5bd6\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
            "    Prop: KERNEL_RELEASE -> '5.10.42-android13-0-00544-"
            "ged21d463f856'\n"
            "    Prop: BRANCH -> 'android13-5.10-2022-05'\n"
            "    Prop: BUILD_NUMBER -> 'ab8295296'\n"
            "    Prop: SPACE -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_LZ4_SIGNATURE1_RSA4096 = (   # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1600 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            20480 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        boot\n'           # boot
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '9d3a0670a9fd3de66e940117ef97700f'
            'ed5fd1c6fb90798fd3873af45fc91cb4\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
            "    Prop: KERNEL_RELEASE -> '5.10.42-android13-0-00544-"
            "ged21d463f856'\n"
            "    Prop: BRANCH -> 'android13-5.10-2022-05'\n"
            "    Prop: BUILD_NUMBER -> 'ab8295296'\n"
            "    Prop: SPACE -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_LZ4_SIGNATURE2_RSA4096 = (   # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1600 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            16384 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        generic_kernel\n' # generic_kernel
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '7d109e3dccca9e30e04249162d07e58c'
            '62fdf269804b35857b956fba339b2679\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
            "    Prop: KERNEL_RELEASE -> '5.10.42-android13-0-00544-"
            "ged21d463f856'\n"
            "    Prop: BRANCH -> 'android13-5.10-2022-05'\n"
            "    Prop: BUILD_NUMBER -> 'ab8295296'\n"
            "    Prop: SPACE -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_GZ_SIGNATURE1_RSA4096 = (    # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1344 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            12288 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        boot\n'           # boot
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '6fcddc6167ae3c2037b424d35c3ef107'
            'f586510dbb2d652d7c08b88e6ea52fc6\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
        )

        self._EXPECTED_BOOT_GZ_SIGNATURE2_RSA4096 = (    # pylint: disable=C0103
            'Minimum libavb version:   1.0\n'
            'Header Block:             256 bytes\n'
            'Authentication Block:     576 bytes\n'
            'Auxiliary Block:          1344 bytes\n'
            'Public key (sha1):        '
            '2597c218aae470a130f61162feaae70afd97f011\n'
            'Algorithm:                SHA256_RSA4096\n'    # RSA4096
            'Rollback Index:           0\n'
            'Flags:                    0\n'
            'Rollback Index Location:  0\n'
            "Release String:           'avbtool 1.2.0'\n"
            'Descriptors:\n'
            '    Hash descriptor:\n'
            '      Image Size:            8192 bytes\n'
            '      Hash Algorithm:        sha256\n'
            '      Partition Name:        generic_kernel\n' # generic_kernel
            '      Salt:                  d00df00d\n'
            '      Digest:                '
            '7a6a43eb4048b783346fb6d039103647'
            '6c4313146da521467af282dff1838d0e\n'
            '      Flags:                 0\n'
            "    Prop: gki -> 'nice'\n"
            "    Prop: space -> 'nice to meet you'\n"
        )

    def _test_boot_signatures(self, signatures_dir, expected_signatures_info):
        """Tests the info of each boot signature under the signature directory.

        Args:
            signatures_dir: the directory containing the boot signatures. e.g.,
                - signatures_dir/boot_signature1
                - signatures_dir/boot_signature2
            expected_signatures_info: A dict containing the expected output
                of `avbtool info_image` for each signature under
                |signatures_dir|. e.g.,
                {'boot_signature1': expected_stdout_signature1
                 'boot_signature2': expected_stdout_signature2}
        """
        for signature in expected_signatures_info:
            avbtool_info_cmds = [
                'avbtool', 'info_image', '--image',
                os.path.join(signatures_dir, signature)
            ]
            result = subprocess.run(avbtool_info_cmds, check=True,
                                    capture_output=True, encoding='utf-8')
            self.assertEqual(result.stdout, expected_signatures_info[signature])

    def test_certify_bootimg_without_avb_footer(self):
        """Tests certify_bootimg on a boot image without an AVB footer."""
        with tempfile.TemporaryDirectory() as temp_out_dir:
            boot_img = os.path.join(temp_out_dir, 'boot.img')
            generate_test_boot_image(boot_img)

            # Generates the certified boot image, with a RSA2048 key.
            boot_certified_img = os.path.join(temp_out_dir,
                                              'boot-certified.img')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img', boot_img,
                '--algorithm', 'SHA256_RSA2048',
                '--key', './testdata/testkey_rsa2048.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified_img,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            extract_boot_signatures(boot_certified_img, temp_out_dir)
            self._test_boot_signatures(
                temp_out_dir,
                {'boot_signature1': self._EXPECTED_BOOT_SIGNATURE_RSA2048,
                 'boot_signature2': self._EXPECTED_KERNEL_SIGNATURE_RSA2048})

            # Generates the certified boot image again, with a RSA4096 key.
            boot_certified2_img = os.path.join(temp_out_dir,
                                              'boot-certified2.img')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img', boot_certified_img,
                '--algorithm', 'SHA256_RSA4096',
                '--key', './testdata/testkey_rsa4096.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified2_img,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            extract_boot_signatures(boot_certified2_img, temp_out_dir)
            self._test_boot_signatures(
                temp_out_dir,
                {'boot_signature1': self._EXPECTED_BOOT_SIGNATURE_RSA4096,
                 'boot_signature2': self._EXPECTED_KERNEL_SIGNATURE_RSA4096})

    def test_certify_bootimg_with_avb_footer(self):
        """Tests the AVB footer location remains after certify_bootimg."""
        with tempfile.TemporaryDirectory() as temp_out_dir:
            boot_img = os.path.join(temp_out_dir, 'boot.img')
            generate_test_boot_image(boot_img=boot_img,
                                     avb_partition_size=128 * 1024)
            self.assertTrue(has_avb_footer(boot_img))

            # Generates the certified boot image, with a RSA2048 key.
            boot_certified_img = os.path.join(temp_out_dir,
                                              'boot-certified.img')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img', boot_img,
                '--algorithm', 'SHA256_RSA2048',
                '--key', './testdata/testkey_rsa2048.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified_img,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            # Checks an AVB footer exists and the image size remains.
            self.assertTrue(has_avb_footer(boot_certified_img))
            self.assertEqual(os.path.getsize(boot_img),
                             os.path.getsize(boot_certified_img))

            extract_boot_signatures(boot_certified_img, temp_out_dir)
            self._test_boot_signatures(
                temp_out_dir,
                {'boot_signature1': self._EXPECTED_BOOT_SIGNATURE_RSA2048,
                 'boot_signature2': self._EXPECTED_KERNEL_SIGNATURE_RSA2048})

            # Generates the certified boot image again, with a RSA4096 key.
            boot_certified2_img = os.path.join(temp_out_dir,
                                              'boot-certified2.img')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img', boot_certified_img,
                '--algorithm', 'SHA256_RSA4096',
                '--key', './testdata/testkey_rsa4096.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified2_img,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            # Checks an AVB footer exists and the image size remains.
            self.assertTrue(has_avb_footer(boot_certified2_img))
            self.assertEqual(os.path.getsize(boot_certified_img),
                             os.path.getsize(boot_certified2_img))

            extract_boot_signatures(boot_certified2_img, temp_out_dir)
            self._test_boot_signatures(
                temp_out_dir,
                {'boot_signature1': self._EXPECTED_BOOT_SIGNATURE_RSA4096,
                 'boot_signature2': self._EXPECTED_KERNEL_SIGNATURE_RSA4096})

    def test_certify_bootimg_with_gki_info(self):
        """Tests certify_bootimg with --gki_info."""
        with tempfile.TemporaryDirectory() as temp_out_dir:
            boot_img = os.path.join(temp_out_dir, 'boot.img')
            generate_test_boot_image(boot_img=boot_img,
                                     avb_partition_size=128 * 1024)
            self.assertTrue(has_avb_footer(boot_img))

            gki_info = ('certify_bootimg_extra_args='
                        '--prop KERNEL_RELEASE:5.10.42'
                        '-android13-0-00544-ged21d463f856 '
                        '--prop BRANCH:android13-5.10-2022-05 '
                        '--prop BUILD_NUMBER:ab8295296 '
                        '--prop GKI_INFO:"added here"\n')
            gki_info_path = os.path.join(temp_out_dir, 'gki-info.txt')
            with open(gki_info_path, 'w', encoding='utf-8') as f:
                f.write(gki_info)

            # Certifies the boot image with --gki_info.
            boot_certified_img = os.path.join(temp_out_dir,
                                              'boot-certified.img')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img', boot_img,
                '--algorithm', 'SHA256_RSA4096',
                '--key', './testdata/testkey_rsa4096.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--gki_info', gki_info_path,
                '--output', boot_certified_img,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            # Checks an AVB footer exists and the image size remains.
            self.assertTrue(has_avb_footer(boot_certified_img))
            self.assertEqual(os.path.getsize(boot_img),
                             os.path.getsize(boot_certified_img))

            extract_boot_signatures(boot_certified_img, temp_out_dir)
            self._test_boot_signatures(
                temp_out_dir,
                {'boot_signature1':
                    self._EXPECTED_BOOT_SIGNATURE_WITH_GKI_INFO,
                 'boot_signature2':
                    self._EXPECTED_KERNEL_SIGNATURE_WITH_GKI_INFO})

    def test_certify_bootimg_exceed_size(self):
        """Tests the boot signature size exceeded max size of the signature."""
        with tempfile.TemporaryDirectory() as temp_out_dir:
            boot_img = os.path.join(temp_out_dir, 'boot.img')
            generate_test_boot_image(boot_img)

            # Certifies the boot.img with many --extra_args, and checks
            # it will raise the ValueError() exception.
            boot_certified_img = os.path.join(temp_out_dir,
                                              'boot-certified.img')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img', boot_img,
                '--algorithm', 'SHA256_RSA2048',
                '--key', './testdata/testkey_rsa2048.pem',
                # Makes it exceed the signature max size.
                '--extra_args', '--prop foo:bar --prop gki:nice ' * 128,
                '--output', boot_certified_img,
            ]

            try:
                subprocess.run(certify_bootimg_cmds, check=True,
                               capture_output=True, cwd=self._exec_dir,
                               encoding='utf-8')
                self.fail('Exceeding signature size assertion is not raised')
            except subprocess.CalledProcessError as err:
                self.assertIn('ValueError: boot_signature size must be <= ',
                              err.stderr)

    def test_certify_bootimg_archive(self):
        """Tests certify_bootimg for a boot images archive.."""
        with tempfile.TemporaryDirectory() as temp_out_dir:
            boot_img_archive_name = os.path.join(temp_out_dir, 'boot-img')
            gki_info = ('certify_bootimg_extra_args='
                        '--prop KERNEL_RELEASE:5.10.42'
                        '-android13-0-00544-ged21d463f856 '
                        '--prop BRANCH:android13-5.10-2022-05 '
                        '--prop BUILD_NUMBER:ab8295296 '
                        '--prop SPACE:"nice to meet you"\n')
            boot_img_archive_path = generate_test_boot_image_archive(
                boot_img_archive_name,
                'gztar',
                # A list of (boot_img_name, kernel_size, partition_size).
                [('boot.img', 8 * 1024, 128 * 1024),
                 ('boot-lz4.img', 16 * 1024, 256 * 1024)],
                gki_info)

            # Certify the boot image archive, with a RSA4096 key.
            boot_certified_img_archive = os.path.join(
                temp_out_dir, 'boot-certified-img.tar.gz')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img_archive', boot_img_archive_path,
                '--algorithm', 'SHA256_RSA4096',
                '--key', './testdata/testkey_rsa4096.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified_img_archive,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            extract_boot_archive_with_signatures(boot_certified_img_archive,
                                                 temp_out_dir)

            # Checks an AVB footer exists and the image size remains.
            boot_img = os.path.join(temp_out_dir, 'boot.img')
            self.assertTrue(has_avb_footer(boot_img))
            self.assertEqual(os.path.getsize(boot_img), 128 * 1024)

            boot_lz4_img = os.path.join(temp_out_dir, 'boot-lz4.img')
            self.assertTrue(has_avb_footer(boot_lz4_img))
            self.assertEqual(os.path.getsize(boot_lz4_img), 256 * 1024)

            self._test_boot_signatures(
                temp_out_dir,
                {'boot/boot_signature1':
                    self._EXPECTED_BOOT_SIGNATURE1_RSA4096,
                 'boot/boot_signature2':
                    self._EXPECTED_BOOT_SIGNATURE2_RSA4096,
                 'boot-lz4/boot_signature1':
                    self._EXPECTED_BOOT_LZ4_SIGNATURE1_RSA4096,
                 'boot-lz4/boot_signature2':
                    self._EXPECTED_BOOT_LZ4_SIGNATURE2_RSA4096})

    def test_certify_bootimg_archive_without_gki_info(self):
        """Tests certify_bootimg for a boot images archive."""
        with tempfile.TemporaryDirectory() as temp_out_dir:
            boot_img_archive_name = os.path.join(temp_out_dir, 'boot-img')

            # Checks ceritfy_bootimg works for a boot images archive without a
            # gki-info.txt. Using *.zip -> *.tar.
            boot_img_archive_path = generate_test_boot_image_archive(
                boot_img_archive_name,
                'zip',
                # A list of (boot_img_name, kernel_size, partition_size).
                [('boot-gz.img', 8 * 1024, 128 * 1024)],
                gki_info=None)
            # Certify the boot image archive, with a RSA4096 key.
            boot_certified_img_archive = os.path.join(
                temp_out_dir, 'boot-certified-img.tar')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img_archive', boot_img_archive_path,
                '--algorithm', 'SHA256_RSA4096',
                '--key', './testdata/testkey_rsa4096.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified_img_archive,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            # Checks ceritfy_bootimg works for a boot images archive with a
            # special gki-info.txt. Using *.tar -> *.tgz.
            boot_img_archive_path = generate_test_boot_image_archive(
                boot_img_archive_name,
                'tar',
                # A list of (boot_img_name, kernel_size, partition_size).
                [('boot-gz.img', 8 * 1024, 128 * 1024)],
                gki_info='a=b\n'
                         'c=d\n')
            # Certify the boot image archive, with a RSA4096 key.
            boot_certified_img_archive2 = os.path.join(
                temp_out_dir, 'boot-certified-img.tgz')
            certify_bootimg_cmds = [
                'certify_bootimg',
                '--boot_img_archive', boot_img_archive_path,
                '--algorithm', 'SHA256_RSA4096',
                '--key', './testdata/testkey_rsa4096.pem',
                '--extra_args', '--prop gki:nice '
                '--prop space:"nice to meet you"',
                '--output', boot_certified_img_archive2,
            ]
            subprocess.run(certify_bootimg_cmds, check=True, cwd=self._exec_dir)

            extract_boot_archive_with_signatures(boot_certified_img_archive2,
                                                 temp_out_dir)

            # Checks an AVB footer exists and the image size remains.
            boot_3_img = os.path.join(temp_out_dir, 'boot-gz.img')
            self.assertTrue(has_avb_footer(boot_3_img))
            self.assertEqual(os.path.getsize(boot_3_img), 128 * 1024)

            self._test_boot_signatures(
                temp_out_dir,
                {'boot-gz/boot_signature1':
                    self._EXPECTED_BOOT_GZ_SIGNATURE1_RSA4096,
                 'boot-gz/boot_signature2':
                    self._EXPECTED_BOOT_GZ_SIGNATURE2_RSA4096})


# I don't know how, but we need both the logger configuration and verbosity
# level > 2 to make atest work. And yes this line needs to be at the very top
# level, not even in the "__main__" indentation block.
logging.basicConfig(stream=sys.stdout)

if __name__ == '__main__':
    unittest.main(verbosity=2)
