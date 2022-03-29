#!/bin/bash
#
# Copyright (C) 2022 The Android Open Source Project
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
#

set -eo errtrace

die() {
  echo >&2 "ERROR:" "${@}"
  exit 1
}

trap 'die "line ${LINENO}, ${FUNCNAME:-<main>}(): \"${BASH_COMMAND}\" returned \"$?\"" ' ERR

# Figure out where we are and where to look for test executables.
cd "$(dirname "${BASH_SOURCE[0]}")"
TEST_DIR="$(pwd)"
readonly TEST_DIR
readonly TEMP_DIR="${TEST_DIR}/stage.retrofit_gki_test"

export PATH="${TEST_DIR}:${PATH}"
rm -rf "${TEMP_DIR}"
mkdir -p "${TEMP_DIR}"

# Generate some test files.
readonly TEST_DTB="${TEMP_DIR}/dtb"
readonly TEST_KERNEL="${TEMP_DIR}/kernel"
readonly TEST_RAMDISK="${TEMP_DIR}/ramdisk"
readonly TEST_VENDOR_RAMDISK="${TEMP_DIR}/vendor_ramdisk"
readonly TEST_BOOT_SIGNATURE="${TEMP_DIR}/boot.boot_signature"
readonly TEST_V2_RETROFITTED_RAMDISK="${TEMP_DIR}/retrofitted.ramdisk"
readonly TEST_BOOT_IMAGE="${TEMP_DIR}/boot.img"
readonly TEST_INIT_BOOT_IMAGE="${TEMP_DIR}/init_boot.img"
readonly TEST_VENDOR_BOOT_IMAGE="${TEMP_DIR}/vendor_boot.img"

( # Run these in subshell because dd is noisy.
  dd if=/dev/urandom of="${TEST_DTB}" bs=1024 count=10
  dd if=/dev/urandom of="${TEST_KERNEL}" bs=1024 count=10
  dd if=/dev/urandom of="${TEST_RAMDISK}" bs=1024 count=10
  dd if=/dev/urandom of="${TEST_VENDOR_RAMDISK}" bs=1024 count=10
  dd if=/dev/urandom of="${TEST_BOOT_SIGNATURE}" bs=1024 count=16
) 2> /dev/null

cat "${TEST_VENDOR_RAMDISK}" "${TEST_RAMDISK}" > "${TEST_V2_RETROFITTED_RAMDISK}"

mkbootimg \
  --header_version 4 \
  --kernel "${TEST_KERNEL}" \
  --output "${TEST_BOOT_IMAGE}"
cat "${TEST_BOOT_SIGNATURE}" >> "${TEST_BOOT_IMAGE}"
avbtool add_hash_footer --image "${TEST_BOOT_IMAGE}" --partition_name boot --partition_size $((20 << 20))

mkbootimg \
  --header_version 4 \
  --ramdisk "${TEST_RAMDISK}" \
  --output "${TEST_INIT_BOOT_IMAGE}"
mkbootimg \
  --header_version 4 \
  --pagesize 4096 \
  --dtb "${TEST_DTB}" \
  --vendor_ramdisk "${TEST_VENDOR_RAMDISK}" \
  --vendor_boot "${TEST_VENDOR_BOOT_IMAGE}"

readonly RETROFITTED_IMAGE="${TEMP_DIR}/retrofitted_boot.img"
readonly RETROFITTED_IMAGE_DIR="${TEMP_DIR}/retrofitted_boot.img.unpack"
readonly BOOT_SIGNATURE_SIZE=$(( 16 << 10 ))


#
# Begin test.
#
echo >&2 "TEST: retrofit to boot v4"

retrofit_gki.sh \
  --boot "${TEST_BOOT_IMAGE}" \
  --init_boot "${TEST_INIT_BOOT_IMAGE}" \
  --version 4 \
  --output "${RETROFITTED_IMAGE}"

rm -rf "${RETROFITTED_IMAGE_DIR}"
unpack_bootimg --boot_img "${RETROFITTED_IMAGE}" --out "${RETROFITTED_IMAGE_DIR}" > /dev/null
tail -c "${BOOT_SIGNATURE_SIZE}" "${RETROFITTED_IMAGE}" > "${RETROFITTED_IMAGE_DIR}/boot_signature"

cmp -s "${TEST_KERNEL}" "${RETROFITTED_IMAGE_DIR}/kernel" ||
  die "unexpected diff: kernel"
cmp -s "${TEST_RAMDISK}" "${RETROFITTED_IMAGE_DIR}/ramdisk" ||
  die "unexpected diff: ramdisk"
cmp -s "${TEST_BOOT_SIGNATURE}" "${RETROFITTED_IMAGE_DIR}/boot_signature" ||
  die "unexpected diff: boot signature"


echo >&2 "TEST: retrofit to boot v3"

retrofit_gki.sh \
  --boot "${TEST_BOOT_IMAGE}" \
  --init_boot "${TEST_INIT_BOOT_IMAGE}" \
  --version 3 \
  --output "${RETROFITTED_IMAGE}"

rm -rf "${RETROFITTED_IMAGE_DIR}"
unpack_bootimg --boot_img "${RETROFITTED_IMAGE}" --out "${RETROFITTED_IMAGE_DIR}" > /dev/null
tail -c "${BOOT_SIGNATURE_SIZE}" "${RETROFITTED_IMAGE}" > "${RETROFITTED_IMAGE_DIR}/boot_signature"

cmp -s "${TEST_KERNEL}" "${RETROFITTED_IMAGE_DIR}/kernel" ||
  die "unexpected diff: kernel"
cmp -s "${TEST_RAMDISK}" "${RETROFITTED_IMAGE_DIR}/ramdisk" ||
  die "unexpected diff: ramdisk"
cmp -s "${TEST_BOOT_SIGNATURE}" "${RETROFITTED_IMAGE_DIR}/boot_signature" ||
  die "unexpected diff: boot signature"


echo >&2 "TEST: retrofit to boot v2"

retrofit_gki.sh \
  --boot "${TEST_BOOT_IMAGE}" \
  --init_boot "${TEST_INIT_BOOT_IMAGE}" \
  --vendor_boot "${TEST_VENDOR_BOOT_IMAGE}" \
  --version 2 \
  --output "${RETROFITTED_IMAGE}"

rm -rf "${RETROFITTED_IMAGE_DIR}"
unpack_bootimg --boot_img "${RETROFITTED_IMAGE}" --out "${RETROFITTED_IMAGE_DIR}" > /dev/null
tail -c "${BOOT_SIGNATURE_SIZE}" "${RETROFITTED_IMAGE}" > "${RETROFITTED_IMAGE_DIR}/boot_signature"

cmp -s "${TEST_DTB}" "${RETROFITTED_IMAGE_DIR}/dtb" ||
  die "unexpected diff: dtb"
cmp -s "${TEST_KERNEL}" "${RETROFITTED_IMAGE_DIR}/kernel" ||
  die "unexpected diff: kernel"
cmp -s "${TEST_V2_RETROFITTED_RAMDISK}" "${RETROFITTED_IMAGE_DIR}/ramdisk" ||
  die "unexpected diff: ramdisk"
cmp -s "${TEST_BOOT_SIGNATURE}" "${RETROFITTED_IMAGE_DIR}/boot_signature" ||
  die "unexpected diff: boot signature"
