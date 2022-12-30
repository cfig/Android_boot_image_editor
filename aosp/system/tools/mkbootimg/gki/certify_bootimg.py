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
#

"""Certify a GKI boot image by generating and appending its boot_signature."""

from argparse import ArgumentParser
import glob
import os
import shlex
import shutil
import subprocess
import tempfile

from gki.generate_gki_certificate import generate_gki_certificate
from unpack_bootimg import unpack_bootimg

BOOT_SIGNATURE_SIZE = 16 * 1024


def get_kernel(boot_img):
    """Extracts the kernel from |boot_img| and returns it."""
    with tempfile.TemporaryDirectory() as unpack_dir:
        unpack_bootimg(boot_img, unpack_dir)
        with open(os.path.join(unpack_dir, 'kernel'), 'rb') as kernel:
            kernel_bytes = kernel.read()
            assert len(kernel_bytes) > 0
            return kernel_bytes


def add_certificate(boot_img, algorithm, key, extra_args):
    """Appends certificates to the end of the boot image.

    This functions appends two certificates to the end of the |boot_img|:
    the 'boot' certificate and the 'generic_kernel' certificate. The former
    is to certify the entire |boot_img|, while the latter is to certify
    the kernel inside the |boot_img|.
    """

    def generate_certificate(image, certificate_name):
        """Generates the certificate and returns the certificate content."""
        with tempfile.NamedTemporaryFile() as output_certificate:
            generate_gki_certificate(
                image=image, avbtool='avbtool', name=certificate_name,
                algorithm=algorithm, key=key, salt='d00df00d',
                additional_avb_args=extra_args, output=output_certificate.name)
            output_certificate.seek(os.SEEK_SET, 0)
            return output_certificate.read()

    boot_signature_bytes = b''
    boot_signature_bytes += generate_certificate(boot_img, 'boot')

    with tempfile.NamedTemporaryFile() as kernel_img:
        kernel_img.write(get_kernel(boot_img))
        kernel_img.flush()
        boot_signature_bytes += generate_certificate(kernel_img.name,
                                                     'generic_kernel')

    if len(boot_signature_bytes) > BOOT_SIGNATURE_SIZE:
        raise ValueError(
            f'boot_signature size must be <= {BOOT_SIGNATURE_SIZE}')
    boot_signature_bytes += (
        b'\0' * (BOOT_SIGNATURE_SIZE - len(boot_signature_bytes)))
    assert len(boot_signature_bytes) == BOOT_SIGNATURE_SIZE

    with open(boot_img, 'ab') as f:
        f.write(boot_signature_bytes)


def erase_certificate_and_avb_footer(boot_img):
    """Erases the boot certificate and avb footer.

    A boot image might already contain a certificate and/or a AVB footer.
    This function erases these additional metadata from the |boot_img|.
    """
    # Tries to erase the AVB footer first, which may or may not exist.
    avbtool_cmd = ['avbtool', 'erase_footer', '--image', boot_img]
    subprocess.run(avbtool_cmd, check=False, stderr=subprocess.DEVNULL)
    assert os.path.getsize(boot_img) > 0

    # No boot signature to erase, just return.
    if os.path.getsize(boot_img) <= BOOT_SIGNATURE_SIZE:
        return

    # Checks if the last 16K is a boot signature, then erases it.
    with open(boot_img, 'rb') as image:
        image.seek(-BOOT_SIGNATURE_SIZE, os.SEEK_END)
        boot_signature = image.read(BOOT_SIGNATURE_SIZE)
        assert len(boot_signature) == BOOT_SIGNATURE_SIZE

    with tempfile.NamedTemporaryFile() as signature_tmpfile:
        signature_tmpfile.write(boot_signature)
        signature_tmpfile.flush()
        avbtool_info_cmd = [
            'avbtool', 'info_image', '--image', signature_tmpfile.name]
        result = subprocess.run(avbtool_info_cmd, check=False,
                                stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL)
        has_boot_signature = (result.returncode == 0)

    if has_boot_signature:
        new_file_size = os.path.getsize(boot_img) - BOOT_SIGNATURE_SIZE
        os.truncate(boot_img, new_file_size)

    assert os.path.getsize(boot_img) > 0


def get_avb_image_size(image):
    """Returns the image size if there is a AVB footer, else return zero."""

    avbtool_info_cmd = ['avbtool', 'info_image', '--image', image]
    result = subprocess.run(avbtool_info_cmd, check=False,
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL)

    if result.returncode == 0:
        return os.path.getsize(image)

    return 0


def add_avb_footer(image, partition_size, extra_footer_args):
    """Appends a AVB hash footer to the image."""

    avbtool_cmd = ['avbtool', 'add_hash_footer', '--image', image,
                   '--partition_name', 'boot']

    if partition_size:
        avbtool_cmd.extend(['--partition_size', str(partition_size)])
    else:
        avbtool_cmd.extend(['--dynamic_partition_size'])

    avbtool_cmd.extend(extra_footer_args)
    subprocess.check_call(avbtool_cmd)


def load_dict_from_file(path):
    """Loads key=value pairs from |path| and returns a dict."""
    d = {}
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' in line:
                name, value = line.split('=', 1)
                d[name] = value
    return d


def load_gki_info_file(gki_info_file, extra_args, extra_footer_args):
    """Loads extra arguments from the gki info file.

    Args:
        gki_info_file: path to a gki-info.txt.
        extra_args: the extra arguments forwarded to avbtool when creating
          the gki certificate.
        extra_footer_args: the extra arguments forwarded to avbtool when
          creating the avb footer.

    """
    info_dict = load_dict_from_file(gki_info_file)
    if 'certify_bootimg_extra_args' in info_dict:
        extra_args.extend(
            shlex.split(info_dict['certify_bootimg_extra_args']))
    if 'certify_bootimg_extra_footer_args' in info_dict:
        extra_footer_args.extend(
            shlex.split(info_dict['certify_bootimg_extra_footer_args']))


def get_archive_name_and_format_for_shutil(path):
    """Returns archive name and format to shutil.make_archive() for the |path|.

    e.g., returns ('/path/to/boot-img', 'gztar') if |path| is
    '/path/to/boot-img.tar.gz'.
    """
    for format_name, format_extensions, _ in shutil.get_unpack_formats():
        for extension in format_extensions:
            if path.endswith(extension):
                return path[:-len(extension)], format_name

    raise ValueError(f"Unsupported archive format: '{path}'")


def parse_cmdline():
    """Parse command-line options."""
    parser = ArgumentParser(add_help=True)

    # Required args.
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument(
        '--boot_img', help='path to the boot image to certify')
    input_group.add_argument(
        '--boot_img_archive', help='path to the boot images archive to certify')

    parser.add_argument('--algorithm', required=True,
                        help='signing algorithm for the certificate')
    parser.add_argument('--key', required=True,
                        help='path to the RSA private key')
    parser.add_argument('--gki_info',
                        help='path to a gki-info.txt to append additional'
                             'properties into the boot signature')
    parser.add_argument('-o', '--output', required=True,
                        help='output file name')

    # Optional args.
    parser.add_argument('--extra_args', default=[], action='append',
                        help='extra arguments to be forwarded to avbtool')
    parser.add_argument('--extra_footer_args', default=[], action='append',
                        help='extra arguments for adding the avb footer')

    args = parser.parse_args()

    if args.gki_info and args.boot_img_archive:
        parser.error('--gki_info cannot be used with --boot_image_archive. '
                     'The gki_info file should be included in the archive.')

    extra_args = []
    for a in args.extra_args:
        extra_args.extend(shlex.split(a))
    args.extra_args = extra_args

    extra_footer_args = []
    for a in args.extra_footer_args:
        extra_footer_args.extend(shlex.split(a))
    args.extra_footer_args = extra_footer_args

    if args.gki_info:
        load_gki_info_file(args.gki_info,
                           args.extra_args,
                           args.extra_footer_args)

    return args


def certify_bootimg(boot_img, output_img, algorithm, key, extra_args,
                    extra_footer_args):
    """Certify a GKI boot image by generating and appending a boot_signature."""
    with tempfile.TemporaryDirectory() as temp_dir:
        boot_tmp = os.path.join(temp_dir, 'boot.tmp')
        shutil.copy2(boot_img, boot_tmp)

        erase_certificate_and_avb_footer(boot_tmp)
        add_certificate(boot_tmp, algorithm, key, extra_args)

        avb_partition_size = get_avb_image_size(boot_img)
        add_avb_footer(boot_tmp, avb_partition_size, extra_footer_args)

        # We're done, copy the temp image to the final output.
        shutil.copy2(boot_tmp, output_img)


def certify_bootimg_archive(boot_img_archive, output_archive,
                            algorithm, key, extra_args, extra_footer_args):
    """Similar to certify_bootimg(), but for an archive of boot images."""
    with tempfile.TemporaryDirectory() as unpack_dir:
        shutil.unpack_archive(boot_img_archive, unpack_dir)

        gki_info_file = os.path.join(unpack_dir, 'gki-info.txt')
        if os.path.exists(gki_info_file):
            load_gki_info_file(gki_info_file, extra_args, extra_footer_args)

        for boot_img in glob.glob(os.path.join(unpack_dir, 'boot*.img')):
            print(f'Certifying {os.path.basename(boot_img)} ...')
            certify_bootimg(boot_img=boot_img, output_img=boot_img,
                            algorithm=algorithm, key=key, extra_args=extra_args,
                            extra_footer_args=extra_footer_args)

        print(f'Making certified archive: {output_archive}')
        archive_file_name, archive_format = (
            get_archive_name_and_format_for_shutil(output_archive))
        built_archive = shutil.make_archive(archive_file_name,
                                            archive_format,
                                            unpack_dir)
        # shutil.make_archive() builds *.tar.gz when then |archive_format| is
        # 'gztar'. However, the end user might specify |output_archive| with
        # *.tgz. Renaming *.tar.gz to *.tgz for this case.
        if built_archive != os.path.realpath(output_archive):
            print(f'Renaming {built_archive} -> {output_archive} ...')
            os.rename(built_archive, output_archive)


def main():
    """Parse arguments and certify the boot image."""
    args = parse_cmdline()

    if args.boot_img_archive:
        certify_bootimg_archive(args.boot_img_archive, args.output,
                                args.algorithm, args.key, args.extra_args,
                                args.extra_footer_args)
    else:
        certify_bootimg(args.boot_img, args.output, args.algorithm,
                        args.key, args.extra_args, args.extra_footer_args)


if __name__ == '__main__':
    main()
