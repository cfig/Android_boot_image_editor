#!/usr/bin/env python
#
# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
A tool to extract kernel information from a kernel image.
"""

import argparse
import subprocess
import sys
import re

CONFIG_PREFIX = b'IKCFG_ST'
GZIP_HEADER = b'\037\213\010'
COMPRESSION_ALGO = (
    (["gzip", "-d"], GZIP_HEADER),
    (["xz", "-d"], b'\3757zXZ\000'),
    (["bzip2", "-d"], b'BZh'),
    (["lz4", "-d", "-l"], b'\002\041\114\030'),

    # These are not supported in the build system yet.
    # (["unlzma"], b'\135\0\0\0'),
    # (["lzop", "-d"], b'\211\114\132'),
)

# "Linux version " UTS_RELEASE " (" LINUX_COMPILE_BY "@"
# LINUX_COMPILE_HOST ") (" LINUX_COMPILER ") " UTS_VERSION "\n";
LINUX_BANNER_PREFIX = b'Linux version '
LINUX_BANNER_REGEX = LINUX_BANNER_PREFIX.decode() + \
    r'(?P<release>(?P<version>[0-9]+[.][0-9]+[.][0-9]+).*) \(.*@.*\) \((?P<compiler>.*)\) .*\n'


def get_from_release(input_bytes, start_idx, key):
  null_idx = input_bytes.find(b'\x00', start_idx)
  if null_idx < 0:
    return None
  try:
    linux_banner = input_bytes[start_idx:null_idx].decode()
  except UnicodeDecodeError:
    return None
  mo = re.match(LINUX_BANNER_REGEX, linux_banner)
  if mo:
    return mo.group(key)
  return None


def dump_from_release(input_bytes, key):
  """
  Helper of dump_version and dump_release
  """
  idx = 0
  while True:
    idx = input_bytes.find(LINUX_BANNER_PREFIX, idx)
    if idx < 0:
      return None

    value = get_from_release(input_bytes, idx, key)
    if value:
      return value.encode()

    idx += len(LINUX_BANNER_PREFIX)


def dump_version(input_bytes):
  """
  Dump kernel version, w.x.y, from input_bytes. Search for the string
  "Linux version " and do pattern matching after it. See LINUX_BANNER_REGEX.
  """
  return dump_from_release(input_bytes, "version")


def dump_compiler(input_bytes):
  """
  Dump kernel version, w.x.y, from input_bytes. Search for the string
  "Linux version " and do pattern matching after it. See LINUX_BANNER_REGEX.
  """
  return dump_from_release(input_bytes, "compiler")


def dump_release(input_bytes):
  """
  Dump kernel release, w.x.y-..., from input_bytes. Search for the string
  "Linux version " and do pattern matching after it. See LINUX_BANNER_REGEX.
  """
  return dump_from_release(input_bytes, "release")


def dump_configs(input_bytes):
  """
  Dump kernel configuration from input_bytes. This can be done when
  CONFIG_IKCONFIG is enabled, which is a requirement on Treble devices.

  The kernel configuration is archived in GZip format right after the magic
  string 'IKCFG_ST' in the built kernel.
  """

  # Search for magic string + GZip header
  idx = input_bytes.find(CONFIG_PREFIX + GZIP_HEADER)
  if idx < 0:
    return None

  # Seek to the start of the archive
  idx += len(CONFIG_PREFIX)

  sp = subprocess.Popen(["gzip", "-d", "-c"], stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  o, _ = sp.communicate(input=input_bytes[idx:])
  if sp.returncode == 1: # error
    return None

  # success or trailing garbage warning
  assert sp.returncode in (0, 2), sp.returncode

  return o


def try_decompress_bytes(cmd, input_bytes):
  sp = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE)
  o, _ = sp.communicate(input=input_bytes)
  # ignore errors
  return o


def try_decompress(cmd, search_bytes, input_bytes):
  idx = 0
  while True:
    idx = input_bytes.find(search_bytes, idx)
    if idx < 0:
      return

    yield try_decompress_bytes(cmd, input_bytes[idx:])
    idx += 1


def decompress_dump(func, input_bytes):
  """
  Run func(input_bytes) first; and if that fails (returns value evaluates to
  False), then try different decompression algorithm before running func.
  """
  o = func(input_bytes)
  if o:
    return o
  for cmd, search_bytes in COMPRESSION_ALGO:
    for decompressed in try_decompress(cmd, search_bytes, input_bytes):
      if decompressed:
        o = decompress_dump(func, decompressed)
        if o:
          return o
    # Force decompress the whole file even if header doesn't match
    decompressed = try_decompress_bytes(cmd, input_bytes)
    if decompressed:
      o = decompress_dump(func, decompressed)
      if o:
        return o


def dump_to_file(f, dump_fn, input_bytes, desc):
  """
  Call decompress_dump(dump_fn, input_bytes) and write to f. If it fails, return
  False; otherwise return True.
  """
  if f is not None:
    o = decompress_dump(dump_fn, input_bytes)
    if o:
      f.write(o)
    else:
      sys.stderr.write(
          "Cannot extract kernel {}".format(desc))
      return False
  return True

def to_bytes_io(b):
  """
  Make b, which is either sys.stdout or sys.stdin, receive bytes as arguments.
  """
  return b.buffer if sys.version_info.major == 3 else b

def main():
  parser = argparse.ArgumentParser(
      formatter_class=argparse.RawTextHelpFormatter,
      description=__doc__ +
      "\nThese algorithms are tried when decompressing the image:\n    " +
      " ".join(tup[0][0] for tup in COMPRESSION_ALGO))
  parser.add_argument('--input',
                      help='Input kernel image. If not specified, use stdin',
                      metavar='FILE',
                      type=argparse.FileType('rb'),
                      default=to_bytes_io(sys.stdin))
  parser.add_argument('--output-configs',
                      help='If specified, write configs. Use stdout if no file '
                           'is specified.',
                      metavar='FILE',
                      nargs='?',
                      type=argparse.FileType('wb'),
                      const=to_bytes_io(sys.stdout))
  parser.add_argument('--output-version',
                      help='If specified, write version. Use stdout if no file '
                           'is specified.',
                      metavar='FILE',
                      nargs='?',
                      type=argparse.FileType('wb'),
                      const=to_bytes_io(sys.stdout))
  parser.add_argument('--output-release',
                      help='If specified, write kernel release. Use stdout if '
                           'no file is specified.',
                      metavar='FILE',
                      nargs='?',
                      type=argparse.FileType('wb'),
                      const=to_bytes_io(sys.stdout))
  parser.add_argument('--output-compiler',
                      help='If specified, write the compiler information. Use stdout if no file '
                           'is specified.',
                      metavar='FILE',
                      nargs='?',
                      type=argparse.FileType('wb'),
                      const=to_bytes_io(sys.stdout))
  parser.add_argument('--tools',
                      help='Decompression tools to use. If not specified, PATH '
                           'is searched.',
                      metavar='ALGORITHM:EXECUTABLE',
                      nargs='*')
  args = parser.parse_args()

  tools = {pair[0]: pair[1]
           for pair in (token.split(':') for token in args.tools or [])}
  for cmd, _ in COMPRESSION_ALGO:
    if cmd[0] in tools:
      cmd[0] = tools[cmd[0]]

  input_bytes = args.input.read()

  ret = 0
  if not dump_to_file(args.output_configs, dump_configs, input_bytes,
                      "configs in {}".format(args.input.name)):
    ret = 1
  if not dump_to_file(args.output_version, dump_version, input_bytes,
                      "version in {}".format(args.input.name)):
    ret = 1
  if not dump_to_file(args.output_release, dump_release, input_bytes,
                      "kernel release in {}".format(args.input.name)):
    ret = 1

  if not dump_to_file(args.output_compiler, dump_compiler, input_bytes,
                      "kernel compiler in {}".format(args.input.name)):
    ret = 1

  return ret


if __name__ == '__main__':
  sys.exit(main())
