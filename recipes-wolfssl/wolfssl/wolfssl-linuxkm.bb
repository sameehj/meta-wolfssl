SUMMARY = "wolfSSL Linux kernel module (libwolfssl.ko)"
DESCRIPTION = "Out-of-tree Linux kernel module for wolfSSL/wolfCrypt"
LICENSE = "GPL-3.0-only"
DEPENDS += "virtual/kernel openssl-native"
LIC_FILES_CHKSUM = "file://COPYING;md5=d32239bcb673463ab874e80d47fae504"

# Build for target kernel
inherit autotools pkgconfig wolfssl-helper module

# Skip the package check for wolfssl itself (it's the base library)
deltask do_wolfssl_check_package

# Fetch wolfSSL from upstream GitHub
SRC_URI = "git://github.com/wolfSSL/wolfssl.git;protocol=https;tag=v5.8.4-stable"
SRC_URI += "file://0001-linuxkm-Fix-spinlock-initialization-on-Tegra-kernels.patch"


# tag=v5.8.4-stable
#SRCREV  = "59f4fa568615396fbf381b073b220d1e8d61e4c2"

# After git fetch, S is the git checkout
S = "${WORKDIR}/git"

# Build in-tree; wolfSSL's configure expects linuxkm/ under the build dir
B = "${S}"

# Build depends on the kernel
DEPENDS += "virtual/kernel"

# Don't split out debug symbols into .debug dirs for this recipe
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

# Make sure we package the .ko
PACKAGES = "${PN}"
FILES:${PN} += "${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/libwolfssl.ko"
FILES:${PN} += "/etc/modules-load.d/wolfssl.conf"

# Skip package QA warnings for kernel modules
INSANE_SKIP:${PN} += "buildpaths debug-files"
INSANE_SKIP:kernel-module-libwolfssl += "buildpaths"

EXTRA_OECONF = " \
    --enable-linuxkm \
    --host=${HOST_SYS} \
    --build=${BUILD_SYS} \
    --with-linux-source=${STAGING_KERNEL_BUILDDIR} \
    --enable-all-crypto \
    --enable-crypttests \
"

# We use the in-tree linuxkm Kbuild rather than the standalone Makefile
do_compile() {
    # Avoid host CFLAGS interfering with kernel build
    unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS

    # build user mode build first
    oe_runmake
}

do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${S}/linuxkm/libwolfssl.ko \
        ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
}

# Remove debug directory if present
do_install:append() {
    install -d ${D}/etc/modules-load.d
    echo "libwolfssl" > ${D}/etc/modules-load.d/wolfssl.conf
}

# Let module.bbclass handle the rest: depmod, packaging, etc.
