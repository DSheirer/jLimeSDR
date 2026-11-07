#!/usr/bin/env bash
# Builds libusb and the LimeSuiteNG legacy API as shared libraries for the host
# platform and copies them to dist/native/<platform>. Works on Linux and macOS.
#
# Usage: native/build-unix.sh [limesuiteng-git-ref]
#
# The ref may be a branch, a tag or a full commit hash. libusb stays a separate
# shared library because it is LGPL-2.1: keeping it out of LimeSuiteNG lets
# users replace it. It is built without udev so the Linux binaries do not need
# libudev at runtime. See jlimesdr.cmake for how LimeSuiteNG is linked.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=versions.env
source "$ROOT/native/versions.env"
REF="${1:-$LIMESUITENG_REF}"
WORK="${JLIMESDR_WORK_DIR:-$ROOT/build/native-work}"
JOBS="$(getconf _NPROCESSORS_ONLN)"

case "$(uname -s)" in
    Linux)  OS=linux ;;
    Darwin) OS=macos ;;
    *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
    x86_64|amd64)  ARCH=x86_64 ;;
    aarch64|arm64) ARCH=aarch64 ;;
    *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac
PLATFORM="$OS-$ARCH"
OUT="$ROOT/dist/native/$PLATFORM"

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT" "$ROOT/dist/native/licenses"
cd "$WORK"

echo "=== libusb $LIBUSB_VERSION ==="
TARBALL="libusb-$LIBUSB_VERSION.tar.bz2"
curl -fsSL -o "$TARBALL" \
    "https://github.com/libusb/libusb/releases/download/v$LIBUSB_VERSION/$TARBALL"
if command -v sha256sum >/dev/null; then
    echo "$LIBUSB_TARBALL_SHA256  $TARBALL" | sha256sum -c -
else
    echo "$LIBUSB_TARBALL_SHA256  $TARBALL" | shasum -a 256 -c -
fi
tar xjf "$TARBALL"

MIN_MACOS=11.0
USB_PREFIX="$WORK/usb"
USB_ARGS=(--prefix="$USB_PREFIX" --disable-static --enable-shared)
if [ "$OS" = linux ]; then
    USB_ARGS+=(--disable-udev)
else
    export CFLAGS="-O2 -mmacosx-version-min=$MIN_MACOS"
    export LDFLAGS="-mmacosx-version-min=$MIN_MACOS"
fi
(cd "libusb-$LIBUSB_VERSION" && ./configure "${USB_ARGS[@]}" >/dev/null && make -j"$JOBS" install >/dev/null)
unset CFLAGS LDFLAGS

echo "=== LimeSuiteNG $REF ==="
# Fetching by ref, rather than cloning a branch, also accepts a commit hash.
# The .git directory stays: LimeSuiteNG puts the commit in its version string.
git init --quiet limesuiteng
git -C limesuiteng fetch --quiet --depth 1 "$LIMESUITENG_REPOSITORY" "$REF"
git -c advice.detachedHead=false -C limesuiteng checkout --quiet FETCH_HEAD
UPSTREAM_COMMIT="$(git -C limesuiteng rev-parse HEAD)"
HEADER=limesuiteng/src/legacyapi/include/lime/LimeSuite.h
UPSTREAM_VERSION="$(sed -n '/^project(/,/)/s/^[[:space:]]*VERSION \([0-9.]*\)[[:space:]]*$/\1/p' \
    limesuiteng/CMakeLists.txt | head -n 1)"
if [ -z "$UPSTREAM_VERSION" ]; then
    echo "No project version in CMakeLists.txt at $REF" >&2
    exit 1
fi
if [ "$REF" = "$LIMESUITENG_REF" ] && [ "$UPSTREAM_VERSION" != "$LIMESUITENG_VERSION" ]; then
    echo "CMakeLists.txt at $REF names version $UPSTREAM_VERSION, not $LIMESUITENG_VERSION" >&2
    exit 1
fi

CMAKE_ARGS=(
    -S limesuiteng -B build-limesuiteng
    -DCMAKE_BUILD_TYPE=Release
    -DCMAKE_PROJECT_limesuiteng_INCLUDE="$ROOT/native/jlimesdr.cmake"
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    -DBUILD_SHARED_LIBS=OFF
    -DBUILD_LEGACYAPI=OFF
    -DBUILD_CLI=OFF
    -DBUILD_GUI=OFF
    -DBUILD_PLUGINS=OFF
    -DBUILD_DOCS=OFF
    -DBUILD_DRIVERS=OFF
    -DINSTALL_UDEV_RULES=OFF
    -DENABLE_UNIT_TESTS=OFF
    -DLIBUSB_1_INCLUDE_DIRS="$USB_PREFIX/include/libusb-1.0"
)
if [ "$OS" = linux ]; then
    USB_LIB="$USB_PREFIX/lib/libusb-1.0.so"
    # $ORIGIN only: the build tree's paths must not leak into the shipped library.
    CMAKE_ARGS+=(-DCMAKE_SKIP_BUILD_RPATH=OFF -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON
        -DCMAKE_INSTALL_RPATH='$ORIGIN' -DCMAKE_INSTALL_RPATH_USE_LINK_PATH=OFF)
else
    USB_LIB="$USB_PREFIX/lib/libusb-1.0.0.dylib"
    CMAKE_ARGS+=(-DCMAKE_OSX_DEPLOYMENT_TARGET="$MIN_MACOS")
fi
cmake "${CMAKE_ARGS[@]}" -DLIBUSB_1_LIBRARIES="$USB_LIB" >/dev/null
cmake --build build-limesuiteng --target limesuiteng-legacyapi --parallel "$JOBS" >/dev/null

echo "=== packaging $PLATFORM ==="
if [ "$OS" = linux ]; then
    cp -L "$USB_PREFIX/lib/libusb-1.0.so.0" "$OUT/libusb-1.0.so.0"
    cp -L "$(find build-limesuiteng -name liblimesuiteng-legacyapi.so | head -n 1)" "$OUT/liblimesuiteng-legacyapi.so"
    chmod 755 "$OUT"/*
    # Only glibc and libusb may remain as dependencies.
    readelf -d "$OUT/liblimesuiteng-legacyapi.so" | grep -E 'NEEDED|RUNPATH|RPATH'
    if readelf -d "$OUT/liblimesuiteng-legacyapi.so" | grep NEEDED | grep -qE 'libstdc\+\+|libgcc_s'; then
        echo "liblimesuiteng-legacyapi.so depends on the C++ runtime" >&2
        exit 1
    fi
    EXPORTED="$(nm -D --defined-only "$OUT/liblimesuiteng-legacyapi.so" | awk '$2 ~ /^[TDBVWRiu]$/ {print $3}')"
else
    cp -L "$USB_PREFIX/lib/libusb-1.0.0.dylib" "$OUT/libusb-1.0.0.dylib"
    cp -L "$(find build-limesuiteng -name liblimesuiteng-legacyapi.dylib | head -n 1)" "$OUT/liblimesuiteng-legacyapi.dylib"
    chmod 755 "$OUT"/*
    # Point the library at the bundled libusb, not the absolute build path, and
    # re-sign because install_name_tool invalidates the ad-hoc signatures.
    install_name_tool -id @rpath/libusb-1.0.0.dylib "$OUT/libusb-1.0.0.dylib"
    install_name_tool -id @rpath/liblimesuiteng-legacyapi.dylib "$OUT/liblimesuiteng-legacyapi.dylib"
    install_name_tool -change "$USB_LIB" @loader_path/libusb-1.0.0.dylib "$OUT/liblimesuiteng-legacyapi.dylib"
    codesign -f -s - "$OUT"/*.dylib
    otool -L "$OUT/liblimesuiteng-legacyapi.dylib"
    if otool -L "$OUT/liblimesuiteng-legacyapi.dylib" | grep -qF "$WORK"; then
        echo "liblimesuiteng-legacyapi still refers to the build directory" >&2
        exit 1
    fi
    EXPORTED="$(nm -gU "$OUT/liblimesuiteng-legacyapi.dylib" | awk '{print $3}' | sed 's/^_//')"
fi
if echo "$EXPORTED" | grep -qv '^LMS_'; then
    echo "liblimesuiteng-legacyapi exports more than the LMS_* functions:" >&2
    echo "$EXPORTED" | grep -v '^LMS_' | head -n 20 >&2
    exit 1
fi

# Pinned sources must match the bindings. Tracking upstream develop only warns,
# so that one new upstream function does not stop the nightly builds.
if ! "$ROOT/native/check-bindings.sh" "$HEADER"; then
    [ "$REF" != "$LIMESUITENG_REF" ] || exit 1
    echo "::warning::Bindings differ from LimeSuite.h at $REF"
fi

cp limesuiteng/COPYING "$ROOT/dist/native/licenses/limesuiteng-LICENSE"
cp "libusb-$LIBUSB_VERSION/COPYING" "$ROOT/dist/native/licenses/libusb-COPYING"
cat > "$OUT/build-info.properties" <<INFO
limesuiteng.ref=$REF
limesuiteng.commit=$UPSTREAM_COMMIT
limesuiteng.version=$UPSTREAM_VERSION
libusb.version=$LIBUSB_VERSION
INFO
ls -l "$OUT"
