#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/u60-hotspot-tests.XXXXXX")
trap 'rm -rf "$test_dir"' EXIT
swiftc -module-cache-path "$test_dir/cache" \
    mobile/ios/OpenU60/Core/Models/DeviceKind.swift \
    mobile/ios/Tests/DeviceKindTests.swift -o "$test_dir/device-kind-tests"
"$test_dir/device-kind-tests"
