SUMMARY = "wolfSSL Linux kernel module (libwolfssl.ko)"
DESCRIPTION = "Out-of-tree Linux kernel module for wolfSSL/wolfCrypt"
LICENSE = "GPL-3.0-only"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = "file://WolfSSL_LicenseAgmt_JAN-2024.pdf;md5=9b56a02d020e92a4bd49d0914e7d7db8"
DEPENDS += "virtual/kernel openssl-native"

# Build for target kernel
inherit module
inherit wolfssl-commercial

# Use the same commercial FIPS bundle as user-mode wolfssl
# These come from gekkos-wolfssl.inc (WOLFSSL_SRC, WOLFSSL_SRC_PASS, WOLFSSL_SRC_SHA)
COMMERCIAL_BUNDLE_ENABLED = "1"
COMMERCIAL_BUNDLE_DIR     = "${THISDIR}/commercial/files"
COMMERCIAL_BUNDLE_NAME    = "${WOLFSSL_SRC}"
COMMERCIAL_BUNDLE_PASS    = "${WOLFSSL_SRC_PASS}"
COMMERCIAL_BUNDLE_SHA     = "${WOLFSSL_SRC_SHA}"
COMMERCIAL_BUNDLE_TARGET  = "${WORKDIR}"

# Fetch the .7z bundle (or README placeholder if not configured)
SRC_URI = "${@ get_commercial_src_uri(d) }"

# After extraction, S points to the top directory of the bundle:
#   ${WORKDIR}/${WOLFSSL_SRC}
S = "${@ get_commercial_source_dir(d) }"

# Build depends on the kernel
DEPENDS += "binutils-cross-${TARGET_ARCH}"

# Make sure we package the .ko
PACKAGES = "${PN}"
FILES:${PN} += "${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/libwolfssl.ko"
SRC_URI += "file://0001-wolfssl-linuxkm-arm64-fixes.patch"
SRC_URI += "file://0001-linuxkm-wc_port-fix.patch;apply=no"

# Optional: tie package arch to machine
PACKAGE_ARCH = "${MACHINE_ARCH}"
# Set kernel arch to target arch
KERNEL_ARCH = "${@map_kernel_arch(d.getVar('TARGET_ARCH'), d)}"
EXTRA_CFLAGS += " -DWOLFCRYPT_FIPS_CORE_DYNAMIC_HASH_VALUE"
EXTRA_CFLAGS += "-mno-outline-atomics -mno-pac-ret -mno-omit-leaf-frame-pointer"
EXTRA_CFLAGS += "-DCONFIG_ARM64_LSE_ATOMICS=0"

do_configure() {
    cd ${S}
    ./configure \
        --host=${HOST_SYS} \
        --build=${BUILD_SYS} \
        --enable-linuxkm \
        --enable-fips=v5.2.4 \
        --with-linux-source="${STAGING_KERNEL_BUILDDIR}" \
        --enable-all-crypto \
        --enable-crypttests


    # Yocto requires a configure step to succeed
    touch ${S}/configured.ok
}

do_compile:prepend() {
    export PATH="${STAGING_BINDIR_TOOLCHAIN}:${PATH}"
}

# We use the in-tree linuxkm Kbuild rather than the standalone Makefile
do_compile() {
    # Avoid host CFLAGS interfering with kernel build
    unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS

    # First pass: create the linuxkm build tree
    # Ignore errors — this step is only to populate files
    oe_runmake || true

    # Now the directory exists, so apply your patch manually
    if [ -f "${S}/linuxkm/wolfcrypt/src/wc_port.c" ]; then
        echo "Applying wc_port late patch..."
        patch -p1 < ${WORKDIR}/0001-wc_port.patch
    else
        echo "wc_port.c not found yet!"
    fi

    echo "Second build pass after successful patch..."
    oe_runmake

    # ${S}/linuxkm contains Kbuild glue for libwolfssl.ko
    # Build against the Yocto kernel headers in ${STAGING_KERNEL_BUILDDIR}
    oe_runmake \
        ARCH=${KERNEL_ARCH} \
        CROSS_COMPILE=${TARGET_PREFIX} \
        KERNEL_SRC=${STAGING_KERNEL_DIR} \
        EXTRA_CFLAGS="-DCONFIG_AS_LSE=0 -DWOLFSSL_NO_THREAD_LS" \
        -C ${STAGING_KERNEL_BUILDDIR} \
        M=${S}/linuxkm \
        modules
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${S}/linuxkm/libwolfssl.ko \
        ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
}

# Let module.bbclass handle the rest: depmod, packaging, etc.
