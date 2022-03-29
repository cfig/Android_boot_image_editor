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

#
# Retrofits GKI boot images for upgrading devices.
#

set -eo errtrace

usage() {
  cat <<EOF
Usage:
  $0 --boot BOOT --init_boot INIT_BOOT --version {3,4} -o OUTPUT
  $0 --boot BOOT --init_boot INIT_BOOT --vendor_boot VENDOR_BOOT --version 2 -o OUTPUT

Options:
  --boot FILE
    Path to the generic boot image.
  --init_boot FILE
    Path to the generic init_boot image.
  --vendor_boot FILE
    Path to the vendor boot image.
  --version {2,3,4}
    Boot image header version to retrofit to.
  -o, --output FILE
    Path to the output boot image.
  -v, --verbose
    Show debug messages.
  -h, --help, --usage
    Show this help message.
EOF
}

die() {
  echo >&2 "ERROR:" "${@}"
  exit 1
}

file_size() {
  stat -c '%s' "$1"
}

get_arg() {
  local arg="$1"
  shift
  while [[ "$#" -gt 0 ]]; do
    if [[ "$1" == "${arg}" ]]; then
      shift
      echo "$1"
      return
    fi
    shift
  done
}

TEMP_DIR="$(mktemp -d --tmpdir retrofit_gki.XXXXXXXX)"
readonly TEMP_DIR

exit_handler() {
  readonly EXIT_CODE="$?"
  rm -rf "${TEMP_DIR}" ||:
  exit "${EXIT_CODE}"
}

trap exit_handler EXIT
trap 'die "line ${LINENO}, ${FUNCNAME:-<main>}(): \"${BASH_COMMAND}\" returned \"$?\"" ' ERR

while [[ "$1" =~ ^- ]]; do
  case "$1" in
    --boot )
      shift
      BOOT_IMAGE="$1"
      ;;
    --init_boot )
      shift
      INIT_BOOT_IMAGE="$1"
      ;;
    --vendor_boot )
      shift
      VENDOR_BOOT_IMAGE="$1"
      ;;
    --version )
      shift
      OUTPUT_BOOT_IMAGE_VERSION="$1"
      ;;
    -o | --output )
      shift
      OUTPUT_BOOT_IMAGE="$1"
      ;;
    -v | --verbose )
      VERBOSE=true
      ;;
    -- )
      shift
      break
      ;;
    -h | --help | --usage )
      usage
      exit 0
      ;;
    * )
      echo >&2 "Unexpected flag: '$1'"
      usage >&2
      exit 1
      ;;
  esac
  shift
done

declare -ir OUTPUT_BOOT_IMAGE_VERSION
readonly BOOT_IMAGE
readonly INIT_BOOT_IMAGE
readonly VENDOR_BOOT_IMAGE
readonly OUTPUT_BOOT_IMAGE
readonly VERBOSE

# Make sure the input arguments make sense.
[[ -f "${BOOT_IMAGE}" ]] ||
  die "argument '--boot': not a regular file: '${BOOT_IMAGE}'"
[[ -f "${INIT_BOOT_IMAGE}" ]] ||
  die "argument '--init_boot': not a regular file: '${INIT_BOOT_IMAGE}'"
if [[ "${OUTPUT_BOOT_IMAGE_VERSION}" -lt 2 ]] || [[ "${OUTPUT_BOOT_IMAGE_VERSION}" -gt 4 ]]; then
  die "argument '--version': valid choices are {2, 3, 4}"
elif [[ "${OUTPUT_BOOT_IMAGE_VERSION}" -eq 2 ]]; then
  [[ -f "${VENDOR_BOOT_IMAGE}" ]] ||
    die "argument '--vendor_boot': not a regular file: '${VENDOR_BOOT_IMAGE}'"
fi
[[ -z "${OUTPUT_BOOT_IMAGE}" ]] &&
  die "argument '--output': cannot be empty"

readonly BOOT_IMAGE_WITHOUT_AVB_FOOTER="${TEMP_DIR}/boot.img.without_avb_footer"
readonly BOOT_DIR="${TEMP_DIR}/boot"
readonly INIT_BOOT_DIR="${TEMP_DIR}/init_boot"
readonly VENDOR_BOOT_DIR="${TEMP_DIR}/vendor_boot"
readonly VENDOR_BOOT_MKBOOTIMG_ARGS="${TEMP_DIR}/vendor_boot.mkbootimg_args"
readonly OUTPUT_RAMDISK="${TEMP_DIR}/out.ramdisk"
readonly OUTPUT_BOOT_SIGNATURE="${TEMP_DIR}/out.boot_signature"

readonly AVBTOOL="${AVBTOOL:-avbtool}"
readonly MKBOOTIMG="${MKBOOTIMG:-mkbootimg}"
readonly UNPACK_BOOTIMG="${UNPACK_BOOTIMG:-unpack_bootimg}"

# Fixed boot signature size for easy discovery in VTS.
readonly BOOT_SIGNATURE_SIZE=$(( 16 << 10 ))


#
# Preparations are done. Now begin the actual work.
#

# Copy the boot image because `avbtool erase_footer` edits the file in-place.
cp "${BOOT_IMAGE}" "${BOOT_IMAGE_WITHOUT_AVB_FOOTER}"
( [[ -n "${VERBOSE}" ]] && set -x
  "${AVBTOOL}" erase_footer --image "${BOOT_IMAGE_WITHOUT_AVB_FOOTER}" 2>/dev/null ||:
  tail -c "${BOOT_SIGNATURE_SIZE}" "${BOOT_IMAGE_WITHOUT_AVB_FOOTER}" > "${OUTPUT_BOOT_SIGNATURE}"
  "${UNPACK_BOOTIMG}" --boot_img "${BOOT_IMAGE}" --out "${BOOT_DIR}" >/dev/null
  "${UNPACK_BOOTIMG}" --boot_img "${INIT_BOOT_IMAGE}" --out "${INIT_BOOT_DIR}" >/dev/null
)
if [[ "$(file_size "${OUTPUT_BOOT_SIGNATURE}")" -ne "${BOOT_SIGNATURE_SIZE}" ]]; then
  die "boot signature size must be equal to ${BOOT_SIGNATURE_SIZE}"
fi

declare -a mkbootimg_args=()

if [[ "${OUTPUT_BOOT_IMAGE_VERSION}" -eq 4 ]]; then
  mkbootimg_args+=( \
    --header_version 4 \
    --kernel "${BOOT_DIR}/kernel" \
    --ramdisk "${INIT_BOOT_DIR}/ramdisk" \
  )
elif [[ "${OUTPUT_BOOT_IMAGE_VERSION}" -eq 3 ]]; then
  mkbootimg_args+=( \
    --header_version 3 \
    --kernel "${BOOT_DIR}/kernel" \
    --ramdisk "${INIT_BOOT_DIR}/ramdisk" \
  )
elif [[ "${OUTPUT_BOOT_IMAGE_VERSION}" -eq 2 ]]; then
  ( [[ -n "${VERBOSE}" ]] && set -x
    "${UNPACK_BOOTIMG}" --boot_img "${VENDOR_BOOT_IMAGE}" --out "${VENDOR_BOOT_DIR}" \
      --format=mkbootimg -0 > "${VENDOR_BOOT_MKBOOTIMG_ARGS}"
    cat "${VENDOR_BOOT_DIR}/vendor_ramdisk" "${INIT_BOOT_DIR}/ramdisk" > "${OUTPUT_RAMDISK}"
  )

  declare -a vendor_boot_args=()
  while IFS= read -r -d '' ARG; do
    vendor_boot_args+=("${ARG}")
  done < "${VENDOR_BOOT_MKBOOTIMG_ARGS}"

  pagesize="$(get_arg --pagesize "${vendor_boot_args[@]}")"
  kernel_offset="$(get_arg --kernel_offset "${vendor_boot_args[@]}")"
  ramdisk_offset="$(get_arg --ramdisk_offset "${vendor_boot_args[@]}")"
  tags_offset="$(get_arg --tags_offset "${vendor_boot_args[@]}")"
  dtb_offset="$(get_arg --dtb_offset "${vendor_boot_args[@]}")"
  kernel_cmdline="$(get_arg --vendor_cmdline "${vendor_boot_args[@]}")"

  mkbootimg_args+=( \
    --header_version 2 \
    --base 0 \
    --kernel_offset "${kernel_offset}" \
    --ramdisk_offset "${ramdisk_offset}" \
    --second_offset 0 \
    --tags_offset "${tags_offset}" \
    --dtb_offset "${dtb_offset}" \
    --cmdline "${kernel_cmdline}" \
    --pagesize "${pagesize}" \
    --kernel "${BOOT_DIR}/kernel" \
    --ramdisk "${OUTPUT_RAMDISK}" \
  )
  if [[ -f "${VENDOR_BOOT_DIR}/dtb" ]]; then
    mkbootimg_args+=(--dtb "${VENDOR_BOOT_DIR}/dtb")
  fi
fi

( [[ -n "${VERBOSE}" ]] && set -x
  "${MKBOOTIMG}" "${mkbootimg_args[@]}" --output "${OUTPUT_BOOT_IMAGE}"
  cat "${OUTPUT_BOOT_SIGNATURE}" >> "${OUTPUT_BOOT_IMAGE}"
)
