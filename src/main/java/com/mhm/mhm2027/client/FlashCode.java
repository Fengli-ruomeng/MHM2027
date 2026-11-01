package com.mhm.mhm2027.client;

/**
 * The code that decrypts across the screen while firmware is flashing. It's set dressing,
 * written for the mod: a made-up gateway firmware loader in C.
 */
final class FlashCode {
    static final String[] LINES = {
            "/* edge gateway firmware loader */",
            "#include <edge/gateway.h>",
            "#include <edge/nvm.h>",
            "",
            "#define BLOCK_SIZE   4096",
            "#define SHARD_MAGIC  0x5D47A1EE",
            "",
            "typedef struct {",
            "    uint32_t magic;",
            "    uint16_t level;",
            "    uint16_t blocks;",
            "    uint8_t  digest[32];",
            "} fw_header_t;",
            "",
            "static int verify_shards(const fw_header_t *hdr, size_t shards)",
            "{",
            "    if (hdr->magic != SHARD_MAGIC)",
            "        return -EBADSHARD;",
            "    return shards >= hdr->blocks ? 0 : -ENOSHARD;",
            "}",
            "",
            "static void take_down(gateway_t *gw)",
            "{",
            "    gw->buffer_mb = 0;",
            "    gw->kernel->state = KERNEL_OFFLINE;",
            "    for (int i = 0; i < gw->nprog; i++)",
            "        prog_halt(&gw->prog[i]);",
            "}",
            "",
            "int flash_firmware(gateway_t *gw, const uint8_t *img, size_t len)",
            "{",
            "    const fw_header_t *hdr = (const fw_header_t *)img;",
            "    int err = verify_shards(hdr, gw->shards);",
            "    if (err)",
            "        return err;",
            "",
            "    take_down(gw);",
            "    for (uint16_t b = 0; b < hdr->blocks; b++) {",
            "        size_t off = sizeof(*hdr) + (size_t)b * BLOCK_SIZE;",
            "        nvm_erase(gw->nvm, b);",
            "        nvm_write(gw->nvm, b, img + off, BLOCK_SIZE);",
            "        if (!nvm_verify(gw->nvm, b, img + off, BLOCK_SIZE))",
            "            return -EFLASH;",
            "        gw_report(gw, b + 1, hdr->blocks);",
            "    }",
            "    gw->level = hdr->level;",
            "    return kernel_reload(gw);",
            "}"
    };

    private FlashCode() {
    }
}
