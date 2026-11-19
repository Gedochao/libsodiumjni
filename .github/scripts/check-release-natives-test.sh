#!/usr/bin/env bash

# Tests the patterns of check-release-natives.sh against actual `file -b` outputs for the
# libsodiumjni native libraries, from several versions of file (whose output format changes): each
# output must match the pattern of its own platform, and none of the others (so that a library
# built for the wrong architecture can't pass the check). Samples of files that aren't shared
# libraries (classifier "-") must match none.
#
# usage: check-release-natives-test.sh

set -euo pipefail

# shellcheck source=SCRIPTDIR/check-release-natives.sh
source "$(dirname "$0")/check-release-natives.sh"

# classifier ; `file -b` output
samples() {
  cat <<'EOF'
x86_64-pc-linux;ELF 64-bit LSB shared object, x86-64, version 1 (SYSV), dynamically linked, BuildID[sha1]=4ff80d972e33051a4c8ba35452eb8d6560ccc3ab, not stripped
aarch64-pc-linux;ELF 64-bit LSB shared object, ARM aarch64, version 1 (SYSV), dynamically linked, BuildID[sha1]=940065595693263b87fc39a992aa10570c91b640, not stripped
x86_64-apple-darwin;Mach-O 64-bit x86_64 dynamically linked shared library, flags:<NOUNDEFS|DYLDLINK|TWOLEVEL|NO_REEXPORTED_DYLIBS>
aarch64-apple-darwin;Mach-O 64-bit arm64 dynamically linked shared library, flags:<NOUNDEFS|DYLDLINK|TWOLEVEL|NO_REEXPORTED_DYLIBS>
x86_64-pc-win32;PE32+ executable (DLL) (GUI) x86-64, for MS Windows, 6 sections
aarch64-pc-win32;PE32+ executable (DLL) (GUI) Aarch64, for MS Windows, 6 sections
x86_64-pc-win32;PE32+ executable for MS Windows 6.00 (DLL), x86-64, 6 sections
aarch64-pc-win32;PE32+ executable for MS Windows 6.02 (DLL), ARM64, 6 sections
x86_64-apple-darwin;Mach-O 64-bit dynamically linked shared library x86_64
aarch64-apple-darwin;Mach-O 64-bit dynamically linked shared library arm64
x86_64-pc-win32;PE32+ executable (DLL) (GUI) x86-64, for MS Windows
aarch64-pc-win32;PE32+ executable (DLL) (GUI) Aarch64, for MS Windows
-;PE32+ executable (console) x86-64, for MS Windows
-;PE32+ executable for MS Windows 6.00 (console), x86-64, 6 sections
-;Mach-O 64-bit executable arm64
-;ELF 64-bit LSB pie executable, x86-64, version 1 (SYSV), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2
EOF
}
# Sources of the samples:
# - Ubuntu 24.04 (file 5.45), GitHub Actions ubuntu-24.04 runner: the first 6 lines
# - Ubuntu 26.04 (newer file, different PE format), GitHub Actions ubuntu-latest runner since
#   November 2026: the PE lines with "for MS Windows 6.0x (DLL)" (its ELF / Mach-O outputs are
#   the same as Ubuntu 24.04's)
# - macOS (file 5.41): the 4 lines after those (its ELF outputs are the same as Ubuntu 24.04's)
# - executables, not shared libraries: the last 4 lines (in the formats above, not captured from
#   actual libsodiumjni builds)

failures=0
count=0
while IFS=';' read -r sample_classifier desc; do
  count=$((count + 1))
  while IFS=';' read -r classifier _ _ _ pattern; do
    if [ "$classifier" = "$sample_classifier" ]; then
      if ! matches "$desc" "$pattern"; then
        echo "FAIL: '$desc' ($sample_classifier) doesn't match its pattern '$pattern'"
        failures=$((failures + 1))
      fi
    elif matches "$desc" "$pattern"; then
      echo "FAIL: '$desc' ($sample_classifier) matches the pattern of $classifier '$pattern'"
      failures=$((failures + 1))
    fi
  done < <(platforms)
done < <(samples)

if [ "$failures" -ne 0 ]; then
  echo "$failures failure(s)" 1>&2
  exit 1
fi
echo "$count samples OK"
