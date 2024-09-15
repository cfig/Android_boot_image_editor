#!/usr/bin/env python3
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

import argparse
import logging
import os
import pkgutil
import subprocess
import sys
import tempfile


def RunCommand(cmd, env):
  """Runs the given command.

  Args:
    cmd: the command represented as a list of strings.
    env: a dictionary of additional environment variables.
  Returns:
    A tuple of the output and the exit code.
  """
  env_copy = os.environ.copy()
  env_copy.update(env)

  cmd[0] = FindProgram(cmd[0])

  logging.info("Env: %s", env)
  logging.info("Running: " + " ".join(cmd))

  p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       env=env_copy, text=True)
  output, _ = p.communicate()

  return output, p.returncode

def FindProgram(prog_name):
  """Finds the path to prog_name.

  Args:
    prog_name: the program name to find.
  Returns:
    path to the progName if found. The program is searched in the same directory
    where this script is located at. If not found, progName is returned.
  """
  exec_dir = os.path.dirname(os.path.realpath(sys.argv[0]))
  prog_path = os.path.join(exec_dir, prog_name)
  if os.path.exists(prog_path):
    return prog_path
  else:
    return prog_name

def ParseArguments(argv):
  """Parses the input arguments to the program."""

  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)

  parser.add_argument("src_dir", help="The source directory for user image.")
  parser.add_argument("output_file", help="The path of the output image file.")
  parser.add_argument("ext_variant", choices=["ext2", "ext4"],
                      help="Variant of the extended filesystem.")
  parser.add_argument("mount_point", help="The mount point for user image.")
  parser.add_argument("fs_size", help="Size of the file system.")
  parser.add_argument("file_contexts", nargs='?',
                      help="The selinux file context.")

  parser.add_argument("--android_sparse", "-s", action="store_true",
                      help="Outputs an android sparse image (mke2fs).")
  parser.add_argument("--journal_size", "-j",
                      help="Journal size (mke2fs).")
  parser.add_argument("--timestamp", "-T",
                      help="Fake timetamp for the output image.")
  parser.add_argument("--fs_config", "-C",
                      help="Path to the fs config file (e2fsdroid).")
  parser.add_argument("--product_out", "-D",
                      help="Path to the directory with device specific fs"
                           " config files (e2fsdroid).")
  parser.add_argument("--block_list_file", "-B",
                      help="Path to the block list file (e2fsdroid).")
  parser.add_argument("--base_alloc_file_in", "-d",
                      help="Path to the input base fs file (e2fsdroid).")
  parser.add_argument("--base_alloc_file_out", "-A",
                      help="Path to the output base fs file (e2fsdroid).")
  parser.add_argument("--label", "-L",
                      help="The mount point (mke2fs).")
  parser.add_argument("--inodes", "-i",
                      help="The extfs inodes count (mke2fs).")
  parser.add_argument("--inode_size", "-I",
                      help="The extfs inode size (mke2fs).")
  parser.add_argument("--reserved_percent", "-M",
                      help="The reserved blocks percentage (mke2fs).")
  parser.add_argument("--flash_erase_block_size", "-e",
                      help="The flash erase block size (mke2fs).")
  parser.add_argument("--flash_logical_block_size", "-o",
                      help="The flash logical block size (mke2fs).")
  parser.add_argument("--mke2fs_uuid", "-U",
                      help="The mke2fs uuid (mke2fs) .")
  parser.add_argument("--mke2fs_hash_seed", "-S",
                      help="The mke2fs hash seed (mke2fs).")
  parser.add_argument("--share_dup_blocks", "-c", action="store_true",
                      help="ext4 share dup blocks (e2fsdroid).")

  args, remainder = parser.parse_known_args(argv)
  # The current argparse doesn't handle intermixed arguments well. Checks
  # manually whether the file_contexts exists as the last argument.
  # TODO(xunchang) use parse_intermixed_args() when we switch to python 3.7.
  if len(remainder) == 1 and remainder[0] == argv[-1]:
    args.file_contexts = remainder[0]
  elif remainder:
    parser.print_usage()
    sys.exit(1)

  return args


def ConstructE2fsCommands(args):
  """Builds the mke2fs & e2fsdroid command based on the input arguments.

  Args:
    args: The result of ArgumentParser after parsing the command line arguments.
  Returns:
    A tuple of two lists that serve as the command for mke2fs and e2fsdroid.
  """

  BLOCKSIZE = 4096

  e2fsdroid_opts = []
  mke2fs_extended_opts = []
  mke2fs_opts = []

  if args.android_sparse:
    mke2fs_extended_opts.append("android_sparse")
  else:
    e2fsdroid_opts.append("-e")
  if args.timestamp:
    e2fsdroid_opts += ["-T", args.timestamp]
  if args.fs_config:
    e2fsdroid_opts += ["-C", args.fs_config]
  if args.product_out:
    e2fsdroid_opts += ["-p", args.product_out]
  if args.block_list_file:
    e2fsdroid_opts += ["-B", args.block_list_file]
  if args.base_alloc_file_in:
    e2fsdroid_opts += ["-d", args.base_alloc_file_in]
  if args.base_alloc_file_out:
    e2fsdroid_opts += ["-D", args.base_alloc_file_out]
  if args.share_dup_blocks:
    e2fsdroid_opts.append("-s")
  if args.file_contexts:
    e2fsdroid_opts += ["-S", args.file_contexts]

  if args.flash_erase_block_size:
    mke2fs_extended_opts.append("stripe_width={}".format(
        int(args.flash_erase_block_size) // BLOCKSIZE))
  if args.flash_logical_block_size:
    # stride should be the max of 8kb and the logical block size
    stride = max(int(args.flash_logical_block_size), 8192)
    mke2fs_extended_opts.append("stride={}".format(stride // BLOCKSIZE))
  if args.mke2fs_hash_seed:
    mke2fs_extended_opts.append("hash_seed=" + args.mke2fs_hash_seed)

  if args.journal_size:
    if args.journal_size == "0":
      mke2fs_opts += ["-O", "^has_journal"]
    else:
      mke2fs_opts += ["-J", "size=" + args.journal_size]
  if args.label:
    mke2fs_opts += ["-L", args.label]
  if args.inodes:
    mke2fs_opts += ["-N", args.inodes]
  if args.inode_size:
    mke2fs_opts += ["-I", args.inode_size]
  if args.mount_point:
    mke2fs_opts += ["-M", args.mount_point]
  if args.reserved_percent:
    mke2fs_opts += ["-m", args.reserved_percent]
  if args.mke2fs_uuid:
    mke2fs_opts += ["-U", args.mke2fs_uuid]
  if mke2fs_extended_opts:
    mke2fs_opts += ["-E", ','.join(mke2fs_extended_opts)]

  # Round down the filesystem length to be a multiple of the block size
  blocks = int(args.fs_size) // BLOCKSIZE
  mke2fs_cmd = (["mke2fs"] + mke2fs_opts +
                ["-t", args.ext_variant, "-b", str(BLOCKSIZE), args.output_file,
                 str(blocks)])

  e2fsdroid_cmd = (["e2fsdroid"] + e2fsdroid_opts +
                   ["-f", args.src_dir, "-a", args.mount_point,
                    args.output_file])

  return mke2fs_cmd, e2fsdroid_cmd


def main(argv):
  logging_format = '%(asctime)s %(filename)s %(levelname)s: %(message)s'
  logging.basicConfig(level=logging.INFO, format=logging_format,
                      datefmt='%H:%M:%S')

  args = ParseArguments(argv)
  if not os.path.isdir(args.src_dir):
    logging.error("Can not find directory %s", args.src_dir)
    sys.exit(2)
  if not args.mount_point:
    logging.error("Mount point is required")
    sys.exit(2)
  if args.mount_point[0] != '/':
    args.mount_point = '/' + args.mount_point
  if not args.fs_size:
    logging.error("Size of the filesystem is required")
    sys.exit(2)

  mke2fs_cmd, e2fsdroid_cmd = ConstructE2fsCommands(args)

  # truncate output file since mke2fs will keep verity section in existing file
  with open(args.output_file, 'w') as output:
    output.truncate()

  # run mke2fs
  with tempfile.NamedTemporaryFile() as conf_file:
    conf_data = pkgutil.get_data('mkuserimg_mke2fs', 'mke2fs.conf')
    conf_file.write(conf_data)
    conf_file.flush()
    mke2fs_env = {"MKE2FS_CONFIG" : conf_file.name}

    if args.timestamp:
      mke2fs_env["E2FSPROGS_FAKE_TIME"] = args.timestamp

    output, ret = RunCommand(mke2fs_cmd, mke2fs_env)
    print(output)
    if ret != 0:
      logging.error("Failed to run mke2fs: " + output)
      sys.exit(4)

  # run e2fsdroid
  e2fsdroid_env = {}
  if args.timestamp:
    e2fsdroid_env["E2FSPROGS_FAKE_TIME"] = args.timestamp

  output, ret = RunCommand(e2fsdroid_cmd, e2fsdroid_env)
  # The build script is parsing the raw output of e2fsdroid; keep the pattern
  # unchanged for now.
  print(output)
  if ret != 0:
    logging.error("Failed to run e2fsdroid_cmd: " + output)
    os.remove(args.output_file)
    sys.exit(4)


if __name__ == '__main__':
  main(sys.argv[1:])
