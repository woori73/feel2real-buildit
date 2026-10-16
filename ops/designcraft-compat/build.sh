#!/usr/bin/env bash
set -euo pipefail
test "$(getconf GNU_LIBC_VERSION)" = 'glibc 2.34'
dnf install -y gcc gcc-c++ make git curl-minimal tar gzip binutils python3 >/dev/null
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs -o /tmp/rustup-init.sh
sh /tmp/rustup-init.sh -y --profile minimal --default-toolchain 1.95.0 >/dev/null
source /root/.cargo/env
git clone --quiet https://github.com/storytold/designcraft.git compat-source
git -C compat-source checkout --quiet 11a51d5b665aec8d6e675bd107a8a01514f10d00
export CARGO_PROFILE_RELEASE_LTO=false CARGO_PROFILE_RELEASE_OPT_LEVEL=2 CARGO_BUILD_JOBS=2
(cd compat-source && cargo build --release --locked -p designcraft-cli)
mkdir -p compat-output/package/bin compat-output/package/licenses
install -m 0755 compat-source/target/release/designcraft-cli compat-output/package/bin/designcraft-cli
cp compat-source/LICENSE* compat-output/package/licenses/
cp compat-source/Cargo.lock compat-output/package/
cp -R compat-source/assets/fonts compat-output/package/licenses/upstream-font-assets
{
  cat /etc/os-release
  getconf GNU_LIBC_VERSION
  rustc -Vv
  cargo -V
  git -C compat-source rev-parse HEAD
  sha256sum compat-source/Cargo.lock compat-output/package/bin/designcraft-cli
  readelf -l compat-output/package/bin/designcraft-cli
} > compat-output/build-provenance.txt
readelf --version-info compat-output/package/bin/designcraft-cli > compat-output/elf-version-info.txt
ldd compat-output/package/bin/designcraft-cli > compat-output/build-ldd.txt
cp compat-output/container-image.txt compat-output/package/
