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
DEPENDS += "virtual/kernel"

# Make sure we package the .ko
PACKAGES = "${PN}"
FILES:${PN} += "${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/libwolfssl.ko"

# Optional: tie package arch to machine
PACKAGE_ARCH = "${MACHINE_ARCH}"
# Set kernel arch to target arch
KERNEL_ARCH = "${@map_kernel_arch(d.getVar('TARGET_ARCH'), d)}"

do_configure() {
    cd ${S}
    ./configure \
        --host=${HOST_SYS} \
        --build=${BUILD_SYS} \
        --enable-linuxkm \
        --with-linux-source="${STAGING_KERNEL_BUILDDIR}" \
        --enable-all-crypto \
        --enable-crypttests
# Currently linuxv5.2.1 doesn't have any support for linuxkm.  v5.2.4 has working amd64 FIPS linuxkm support (again with no asm support), but doesn't yet have working aarch64 support (again just FIPS tooling gaps)
# so disabling FIPS support for now!
#       
#        --enable-fips=v5 \


    # Yocto requires a configure step to succeed
    touch ${S}/configured.ok
}

# We use the in-tree linuxkm Kbuild rather than the standalone Makefile
do_compile() {
    # Avoid host CFLAGS interfering with kernel build
    unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS

    # build user mode build first
    oe_runmake


    # ${S}/linuxkm contains Kbuild glue for libwolfssl.ko
    # Build against the Yocto kernel headers in ${STAGING_KERNEL_BUILDDIR}
    oe_runmake \
        ARCH=${KERNEL_ARCH} \
        CROSS_COMPILE=${TARGET_PREFIX} \
        KERNEL_SRC=${STAGING_KERNEL_DIR} \
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
