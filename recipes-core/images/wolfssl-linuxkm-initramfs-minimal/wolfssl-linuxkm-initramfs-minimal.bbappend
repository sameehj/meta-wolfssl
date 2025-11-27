# Add wolfSSL kernel module to the initramfs image
IMAGE_INSTALL:append = " wolfssl-linuxkm kernel-module-libwolfssl"