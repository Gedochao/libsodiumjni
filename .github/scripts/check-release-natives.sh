#!/usr/bin/env bash

# Checks that a release has the native libraries of all supported platforms, built for the right
# CPU architecture, both bundled in the JAR (loaded by LoadLibrary.initializeFromResources) and as
# separate artifacts (fetched by Scala CLI, with classifiers like aarch64-pc-linux).
#
# usage: check-release-natives.sh <libsodiumjni JAR> <artifacts directory>

set -euo pipefail

# Platforms, one per line (the same as LoadLibrary, the build and the README, checked by
# PlatformTableTests): classifier ; directory under META-INF/native ; shared library extension ;
#   static library extension ; extended regular expression that the `file -b` output of the shared
#   library must match. The output format depends on the version of file (e.g. "PE32+ executable
#   (DLL) (GUI) Aarch64, for MS Windows" with file 5.45, "PE32+ executable for MS Windows 6.02
#   (DLL), ARM64" with later ones): check-release-natives-test.sh checks these patterns against
#   the outputs of several versions.
platforms() {
  cat <<'EOF'
x86_64-pc-linux;linux64;so;a;^ELF 64-bit LSB shared object, x86-64,
aarch64-pc-linux;linux-aarch64;so;a;^ELF 64-bit LSB shared object, ARM aarch64,
x86_64-apple-darwin;darwin;dylib;a;^Mach-O 64-bit (x86_64 dynamically linked shared library|dynamically linked shared library x86_64)
aarch64-apple-darwin;arm-darwin;dylib;a;^Mach-O 64-bit (arm64 dynamically linked shared library|dynamically linked shared library arm64)
x86_64-pc-win32;windows64;dll;lib;^PE32\+ executable.*\(DLL\).*x86-64
aarch64-pc-win32;windows-aarch64;dll;lib;^PE32\+ executable.*\(DLL\).*(Aarch64|ARM64)
EOF
}

# matches <`file -b` output> <extended regular expression>: whether the output matches it (a
# regular expression rather than a glob: in [[ == ]], globs get extended glob semantics in recent
# bash versions, where *(DLL) means "any number of DLL", not "anything, then (DLL)")
matches() {
  [[ "$1" =~ $2 ]]
}

# check <file> <pattern that the output of `file -b` must match>
check() {
  if [ ! -f "$1" ]; then
    echo "MISSING: $1"
    status=1
    return
  fi
  local desc
  desc="$(file -b "$1")"
  if matches "$desc" "$2"; then
    echo "OK: $1: $desc"
  else
    echo "WRONG ARCHITECTURE OR FORMAT: $1: $desc (expected $2)"
    status=1
  fi
}

main() {
  if [ "$#" -ne 2 ]; then
    echo "usage: $0 <libsodiumjni JAR> <artifacts directory>" 1>&2
    exit 2
  fi

  local jar="$1"
  local artifacts="$2"

  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  unzip -q "$jar" 'META-INF/native/*' -d "$tmp" || true

  status=0

  local classifier dir ext static_ext expected
  while IFS=';' read -r classifier dir ext static_ext expected; do
    check "$tmp/META-INF/native/$dir/libsodiumjni.$ext" "$expected"
    check "$artifacts/libsodiumjni-$classifier.$ext" "$expected"
    if [ -f "$artifacts/libsodiumjni-$classifier.$static_ext" ]; then
      echo "OK: $artifacts/libsodiumjni-$classifier.$static_ext"
    else
      echo "MISSING: $artifacts/libsodiumjni-$classifier.$static_ext"
      status=1
    fi
  done < <(platforms)

  if [ "$status" -ne 0 ]; then
    echo "Some native libraries are missing or have the wrong architecture" 1>&2
  fi
  exit "$status"
}

# only run when executed, not when sourced (by check-release-natives-test.sh)
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
