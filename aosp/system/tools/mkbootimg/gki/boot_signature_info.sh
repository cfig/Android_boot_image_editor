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
# Dump boot signature info of a GKI boot image.
#

set -eo errtrace

die() {
  echo >&2 "ERROR:" "${@}"
  exit 1
}

TEMP_DIR="$(mktemp -d)"
readonly TEMP_DIR

exit_handler() {
  readonly EXIT_CODE="$?"
  rm -rf "${TEMP_DIR}" ||:
  exit "${EXIT_CODE}"
}

trap exit_handler EXIT
trap 'die "line ${LINENO}, ${FUNCNAME:-<main>}(): \"${BASH_COMMAND}\" returned \"$?\"" ' ERR

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

readonly VBMETA_IMAGE="${TEMP_DIR}/boot.boot_signature"
readonly VBMETA_IMAGE_TEMP="${VBMETA_IMAGE}.temp"
readonly VBMETA_INFO="${VBMETA_IMAGE}.info"
readonly BOOT_IMAGE="${TEMP_DIR}/boot.img"
readonly BOOT_IMAGE_DIR="${TEMP_DIR}/boot.unpack_dir"
readonly BOOT_IMAGE_ARGS="${TEMP_DIR}/boot.mkbootimg_args"
readonly BOOT_SIGNATURE_SIZE=$(( 16 << 10 ))

[[ -f "$1" ]] ||
  die "expected one input image"
cp "$1" "${BOOT_IMAGE}"

# This could fail if there already is no AVB footer.
avbtool erase_footer --image "${BOOT_IMAGE}" 2>/dev/null ||:

unpack_bootimg --boot_img "${BOOT_IMAGE}" --out "${BOOT_IMAGE_DIR}" \
  --format=mkbootimg -0 > "${BOOT_IMAGE_ARGS}"

declare -a boot_args=()
while IFS= read -r -d '' ARG; do
  boot_args+=("${ARG}")
done < "${BOOT_IMAGE_ARGS}"

BOOT_IMAGE_VERSION="$(get_arg --header_version "${boot_args[@]}")"
if [[ "${BOOT_IMAGE_VERSION}" -ge 4 ]] && [[ -f "${BOOT_IMAGE_DIR}/boot_signature" ]]; then
  cp "${BOOT_IMAGE_DIR}/boot_signature" "${VBMETA_IMAGE}"
else
  tail -c "${BOOT_SIGNATURE_SIZE}" "${BOOT_IMAGE}" > "${VBMETA_IMAGE}"
fi

# Keep carving out vbmeta image from the boot signature until we fail or EOF.
# Failing is fine because there could be padding trailing the boot signature.
while avbtool info_image --image "${VBMETA_IMAGE}" --output "${VBMETA_INFO}" 2>/dev/null; do
  cat "${VBMETA_INFO}"
  echo

  declare -i H A X
  H="$(cat "${VBMETA_INFO}" | grep 'Header Block:' | awk '{print $3}')"
  A="$(cat "${VBMETA_INFO}" | grep 'Authentication Block:' | awk '{print $3}')"
  X="$(cat "${VBMETA_INFO}" | grep 'Auxiliary Block:' | awk '{print $3}')"
  vbmeta_size="$(( ${H} + ${A} + ${X} ))"

  tail -c "+$(( ${vbmeta_size} + 1 ))" "${VBMETA_IMAGE}" > "${VBMETA_IMAGE_TEMP}"
  cp "${VBMETA_IMAGE_TEMP}" "${VBMETA_IMAGE}"
done
