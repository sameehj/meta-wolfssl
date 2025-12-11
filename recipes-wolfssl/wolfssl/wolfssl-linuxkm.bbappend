# wolfSSL LinuxKM Kernel Randomness Patch Support
#
# This bbappend provides optional support for patching the Linux kernel
# to enable wolfSSL random callback hooks in drivers/char/random.c
#
# IMPORTANT: This feature is DISABLED by default because it modifies
# the kernel source and triggers a partial kernel rebuild.
#
# To enable, set in your local.conf or distro config:
#   WOLFSSL_ENABLE_RANDOMNESS_PATCH = "1"
#
# For NVIDIA Tegra (L4T/Jetson) platforms, use:
#   WOLFSSL_LINUXKM_PATCH_DIR = "linuxkm/patches/5.17-ubuntu-jammy-tegra"
#   WOLFSSL_LINUXKM_PATCH_FILE = "WOLFSSL_LINUXKM_HAVE_GET_RANDOM_CALLBACKS-5v17-ubuntu-jammy-tegra.patch"

# === Randomness Patch Toggle ===
# Set to "1" to enable, "0" to disable (default: disabled)
WOLFSSL_ENABLE_RANDOMNESS_PATCH ?= "0"

# === Patch Selection ===
# Override these variables for your specific kernel version
WOLFSSL_LINUXKM_PATCH_DIR ?= "linuxkm/patches/6.12"
WOLFSSL_LINUXKM_PATCH_FILE ?= "WOLFSSL_LINUXKM_HAVE_GET_RANDOM_CALLBACKS-6v12.patch"

# === Available Patches ===
# You can find the available patches in the wolfSSL source tree:
# linuxkm/patches

# === Kernel Paths ===
WOLFSSL_KERNEL_SRC = "${TMPDIR}/work-shared/${MACHINE}/kernel-source"
WOLFSSL_KERNEL_BUILD = "${TMPDIR}/work-shared/${MACHINE}/kernel-build-artifacts"

# Ensure kernel is compiled before we patch
do_patch[depends] += "virtual/kernel:do_compile"

# === Apply Randomness Patch and Rebuild Kernel ===
do_patch[postfuncs] += "wolfssl_apply_randomness_patch"

wolfssl_apply_randomness_patch() {
    if [ "${WOLFSSL_ENABLE_RANDOMNESS_PATCH}" = "1" ]; then
        patch_path="${S}/${WOLFSSL_LINUXKM_PATCH_DIR}/${WOLFSSL_LINUXKM_PATCH_FILE}"
        kernel_src="${WOLFSSL_KERNEL_SRC}"
        kernel_build="${WOLFSSL_KERNEL_BUILD}"

        bbnote "wolfSSL: Randomness patch ENABLED"
        bbnote "wolfSSL: Patch file: $patch_path"
        bbnote "wolfSSL: Kernel source: $kernel_src"
        bbnote "wolfSSL: Kernel build: $kernel_build"

        if [ ! -f "$patch_path" ]; then
            bbfatal "wolfSSL: Patch file not found: $patch_path"
        fi

        if [ ! -d "$kernel_src" ]; then
            bbfatal "wolfSSL: Kernel source not found: $kernel_src"
        fi

        if [ ! -d "$kernel_build" ]; then
            bbfatal "wolfSSL: Kernel build dir not found: $kernel_build"
        fi

        # Check if patch is already applied
        cd "$kernel_src"
        if patch -p1 --reverse --dry-run < "$patch_path" >/dev/null 2>&1; then
            bbnote "wolfSSL: Kernel patch already applied — skipping."
            return 0
        fi

        # Apply the patch
        patch -p1 --forward < "$patch_path" || {
            bbfatal "wolfSSL: Kernel patch failed to apply. Check patch compatibility with your kernel."
        }

        bbnote "wolfSSL: Randomness patch applied successfully."

        # Remove random.o to force rebuild
        rm -f "$kernel_build/drivers/char/random.o"
        bbnote "wolfSSL: Removed random.o to trigger rebuild."

        # Rebuild kernel with patched random.c
        bbnote "wolfSSL: Rebuilding kernel with patched random.c..."

        cd "$kernel_src"

        # Determine architecture
        KARCH="${@'arm64' if d.getVar('TARGET_ARCH') == 'aarch64' else d.getVar('TARGET_ARCH')}"

        # Rebuild random.o
        bbnote "wolfSSL: Compiling drivers/char/random.o..."
        oe_runmake ARCH=$KARCH CROSS_COMPILE=${TARGET_PREFIX} O="$kernel_build" drivers/char/random.o

        # Relink vmlinux
        bbnote "wolfSSL: Relinking vmlinux..."
        oe_runmake ARCH=$KARCH CROSS_COMPILE=${TARGET_PREFIX} O="$kernel_build" vmlinux

        # Rebuild Image
        bbnote "wolfSSL: Rebuilding kernel Image..."
        oe_runmake ARCH=$KARCH CROSS_COMPILE=${TARGET_PREFIX} O="$kernel_build" Image

        bbnote "wolfSSL: Kernel rebuilt with wolfSSL randomness callbacks."
    else
        bbnote "wolfSSL: Randomness patch DISABLED (set WOLFSSL_ENABLE_RANDOMNESS_PATCH=\"1\" to enable)"
    fi
}
