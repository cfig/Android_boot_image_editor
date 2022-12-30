#!/usr/bin/env python3
#
# Copyright 2021, The Android Open Source Project
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

"""Generate a Generic Boot Image certificate suitable for VTS verification."""

from argparse import ArgumentParser
import shlex
import subprocess


def generate_gki_certificate(image, avbtool, name, algorithm, key, salt,
                             additional_avb_args, output):
    """Shell out to avbtool to generate a GKI certificate."""

    # Need to specify a value of --partition_size for avbtool to work.
    # We use 64 MB below, but avbtool will not resize the boot image to
    # this size because --do_not_append_vbmeta_image is also specified.
    avbtool_cmd = [
        avbtool, 'add_hash_footer',
        '--partition_name', name,
        '--partition_size', str(64 * 1024 * 1024),
        '--image', image,
        '--algorithm', algorithm,
        '--key', key,
        '--do_not_append_vbmeta_image',
        '--output_vbmeta_image', output,
    ]

    if salt is not None:
        avbtool_cmd += ['--salt', salt]

    avbtool_cmd += additional_avb_args

    subprocess.check_call(avbtool_cmd)


def parse_cmdline():
    parser = ArgumentParser(add_help=True)

    # Required args.
    parser.add_argument('image', help='path to the image')
    parser.add_argument('-o', '--output', required=True,
                        help='output certificate file name')
    parser.add_argument('--name', required=True,
                        choices=['boot', 'generic_kernel'],
                        help='name of the image to be certified')
    parser.add_argument('--algorithm', required=True,
                        help='AVB signing algorithm')
    parser.add_argument('--key', required=True,
                        help='path to the RSA private key')

    # Optional args.
    parser.add_argument('--avbtool', default='avbtool',
                        help='path to the avbtool executable')
    parser.add_argument('--salt', help='salt to use when computing image hash')
    parser.add_argument('--additional_avb_args', default=[], action='append',
                        help='additional arguments to be forwarded to avbtool')

    args = parser.parse_args()

    additional_avb_args = []
    for a in args.additional_avb_args:
        additional_avb_args.extend(shlex.split(a))
    args.additional_avb_args = additional_avb_args

    return args


def main():
    args = parse_cmdline()
    generate_gki_certificate(
        image=args.image, avbtool=args.avbtool, name=args.name,
        algorithm=args.algorithm, key=args.key, salt=args.salt,
        additional_avb_args=args.additional_avb_args,
        output=args.output,
    )


if __name__ == '__main__':
    main()
