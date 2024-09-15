#define _LARGEFILE64_SOURCE

#define LOG_TAG "f2fs_sparseblock"

#include "f2fs_sparseblock.h"

#include <errno.h>
#include <f2fs_fs.h>
#include <fcntl.h>
#include <linux/types.h>
#include <malloc.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <log/log.h>

#define D_DISP_u32(ptr, member)                                                 \
    do {                                                                        \
        SLOGV("%-30s"                                                           \
              "\t\t[0x%#08x : %u]\n",                                           \
              #member, le32_to_cpu((ptr)->member), le32_to_cpu((ptr)->member)); \
    } while (0);

#define D_DISP_u64(ptr, member)                                                 \
    do {                                                                        \
        SLOGV("%-30s"                                                           \
              "\t\t[0x%#016" PRIx64 " : %" PRIu64 "]\n",                          \
              #member, le64_to_cpu((ptr)->member), le64_to_cpu((ptr)->member)); \
    } while (0);

#define segno_in_journal(jnl, i) ((jnl)->sit_j.entries[i].segno)

#define sit_in_journal(jnl, i) ((jnl)->sit_j.entries[i].se)

static void dbg_print_raw_sb_info(struct f2fs_super_block* sb) {
    SLOGV("\n");
    SLOGV("+--------------------------------------------------------+\n");
    SLOGV("| Super block                                            |\n");
    SLOGV("+--------------------------------------------------------+\n");

    D_DISP_u32(sb, magic);
    D_DISP_u32(sb, major_ver);
    D_DISP_u32(sb, minor_ver);
    D_DISP_u32(sb, log_sectorsize);
    D_DISP_u32(sb, log_sectors_per_block);

    D_DISP_u32(sb, log_blocksize);
    D_DISP_u32(sb, log_blocks_per_seg);
    D_DISP_u32(sb, segs_per_sec);
    D_DISP_u32(sb, secs_per_zone);
    D_DISP_u32(sb, checksum_offset);
    D_DISP_u64(sb, block_count);

    D_DISP_u32(sb, section_count);
    D_DISP_u32(sb, segment_count);
    D_DISP_u32(sb, segment_count_ckpt);
    D_DISP_u32(sb, segment_count_sit);
    D_DISP_u32(sb, segment_count_nat);

    D_DISP_u32(sb, segment_count_ssa);
    D_DISP_u32(sb, segment_count_main);
    D_DISP_u32(sb, segment0_blkaddr);

    D_DISP_u32(sb, cp_blkaddr);
    D_DISP_u32(sb, sit_blkaddr);
    D_DISP_u32(sb, nat_blkaddr);
    D_DISP_u32(sb, ssa_blkaddr);
    D_DISP_u32(sb, main_blkaddr);

    D_DISP_u32(sb, root_ino);
    D_DISP_u32(sb, node_ino);
    D_DISP_u32(sb, meta_ino);
    D_DISP_u32(sb, cp_payload);
    SLOGV("\n");
}
static void dbg_print_raw_ckpt_struct(struct f2fs_checkpoint* cp) {
    SLOGV("\n");
    SLOGV("+--------------------------------------------------------+\n");
    SLOGV("| Checkpoint                                             |\n");
    SLOGV("+--------------------------------------------------------+\n");

    D_DISP_u64(cp, checkpoint_ver);
    D_DISP_u64(cp, user_block_count);
    D_DISP_u64(cp, valid_block_count);
    D_DISP_u32(cp, rsvd_segment_count);
    D_DISP_u32(cp, overprov_segment_count);
    D_DISP_u32(cp, free_segment_count);

    D_DISP_u32(cp, alloc_type[CURSEG_HOT_NODE]);
    D_DISP_u32(cp, alloc_type[CURSEG_WARM_NODE]);
    D_DISP_u32(cp, alloc_type[CURSEG_COLD_NODE]);
    D_DISP_u32(cp, cur_node_segno[0]);
    D_DISP_u32(cp, cur_node_segno[1]);
    D_DISP_u32(cp, cur_node_segno[2]);

    D_DISP_u32(cp, cur_node_blkoff[0]);
    D_DISP_u32(cp, cur_node_blkoff[1]);
    D_DISP_u32(cp, cur_node_blkoff[2]);

    D_DISP_u32(cp, alloc_type[CURSEG_HOT_DATA]);
    D_DISP_u32(cp, alloc_type[CURSEG_WARM_DATA]);
    D_DISP_u32(cp, alloc_type[CURSEG_COLD_DATA]);
    D_DISP_u32(cp, cur_data_segno[0]);
    D_DISP_u32(cp, cur_data_segno[1]);
    D_DISP_u32(cp, cur_data_segno[2]);

    D_DISP_u32(cp, cur_data_blkoff[0]);
    D_DISP_u32(cp, cur_data_blkoff[1]);
    D_DISP_u32(cp, cur_data_blkoff[2]);

    D_DISP_u32(cp, ckpt_flags);
    D_DISP_u32(cp, cp_pack_total_block_count);
    D_DISP_u32(cp, cp_pack_start_sum);
    D_DISP_u32(cp, valid_node_count);
    D_DISP_u32(cp, valid_inode_count);
    D_DISP_u32(cp, next_free_nid);
    D_DISP_u32(cp, sit_ver_bitmap_bytesize);
    D_DISP_u32(cp, nat_ver_bitmap_bytesize);
    D_DISP_u32(cp, checksum_offset);
    D_DISP_u64(cp, elapsed_time);

    D_DISP_u32(cp, sit_nat_version_bitmap[0]);
    SLOGV("\n\n");
}

static void dbg_print_info_struct(struct f2fs_info* info) {
    SLOGV("\n");
    SLOGV("+--------------------------------------------------------+\n");
    SLOGV("| F2FS_INFO                                              |\n");
    SLOGV("+--------------------------------------------------------+\n");
    SLOGV("blocks_per_segment: %" PRIu64, info->blocks_per_segment);
    SLOGV("block_size: %d", info->block_size);
    SLOGV("sit_bmp loc: %p", info->sit_bmp);
    SLOGV("sit_bmp_size: %d", info->sit_bmp_size);
    SLOGV("blocks_per_sit: %" PRIu64, info->blocks_per_sit);
    SLOGV("sit_blocks loc: %p", info->sit_blocks);
    SLOGV("sit_sums loc: %p", info->sit_sums);
    SLOGV("sit_sums num: %d", le16_to_cpu(info->sit_sums->journal.n_sits));
    unsigned int i;
    for (i = 0; i < (le16_to_cpu(info->sit_sums->journal.n_sits)); i++) {
        SLOGV("entry %d in journal entries is for segment %d", i,
              le32_to_cpu(segno_in_journal(&info->sit_sums->journal, i)));
    }

    SLOGV("cp_blkaddr: %" PRIu64, info->cp_blkaddr);
    SLOGV("cp_valid_cp_blkaddr: %" PRIu64, info->cp_valid_cp_blkaddr);
    SLOGV("sit_blkaddr: %" PRIu64, info->sit_blkaddr);
    SLOGV("nat_blkaddr: %" PRIu64, info->nat_blkaddr);
    SLOGV("ssa_blkaddr: %" PRIu64, info->ssa_blkaddr);
    SLOGV("main_blkaddr: %" PRIu64, info->main_blkaddr);
    SLOGV("total_user_used: %" PRIu64, info->total_user_used);
    SLOGV("total_blocks: %" PRIu64, info->total_blocks);
    SLOGV("\n\n");
}

/* read blocks */
static int read_structure(int fd, unsigned long long start, void* buf, ssize_t len) {
    off64_t ret;

    ret = lseek64(fd, start, SEEK_SET);
    if (ret < 0) {
        SLOGE("failed to seek\n");
        return ret;
    }

    ret = read(fd, buf, len);
    if (ret < 0) {
        SLOGE("failed to read\n");
        return ret;
    }
    if (ret != len) {
        SLOGE("failed to read all\n");
        return -1;
    }
    return 0;
}

static int read_structure_blk(int fd, unsigned long long start_blk, void* buf, size_t len) {
    return read_structure(fd, F2FS_BLKSIZE * start_blk, buf, F2FS_BLKSIZE * len);
}

static int read_f2fs_sb(int fd, struct f2fs_super_block* sb) {
    int rc;
    rc = read_structure(fd, F2FS_SUPER_OFFSET, sb, sizeof(*sb));
    if (le32_to_cpu(sb->magic) != F2FS_SUPER_MAGIC) {
        SLOGE("Not a valid F2FS super block. Magic:%#08x != %#08x", le32_to_cpu(sb->magic),
              F2FS_SUPER_MAGIC);
        return -1;
    }
    return 0;
}

unsigned int get_f2fs_filesystem_size_sec(char* dev) {
    int fd;
    if ((fd = open(dev, O_RDONLY)) < 0) {
        SLOGE("Cannot open device to get filesystem size ");
        return 0;
    }
    struct f2fs_super_block sb;
    if (read_f2fs_sb(fd, &sb)) return 0;
    return (unsigned int)(le64_to_cpu(sb.block_count) * F2FS_BLKSIZE / DEFAULT_SECTOR_SIZE);
}

static struct f2fs_checkpoint* validate_checkpoint(block_t cp_addr, unsigned long long* version,
                                                   int fd) {
    unsigned char *cp_block_1, *cp_block_2;
    struct f2fs_checkpoint* cp_block;
    uint64_t cp1_version = 0, cp2_version = 0;

    cp_block_1 = malloc(F2FS_BLKSIZE);
    if (!cp_block_1) return NULL;

    /* Read the 1st cp block in this CP pack */
    if (read_structure_blk(fd, cp_addr, cp_block_1, 1)) goto invalid_cp1;

    /* get the version number */
    cp_block = (struct f2fs_checkpoint*)cp_block_1;

    cp1_version = le64_to_cpu(cp_block->checkpoint_ver);

    cp_block_2 = malloc(F2FS_BLKSIZE);
    if (!cp_block_2) {
        goto invalid_cp1;
    }
    /* Read the 2nd cp block in this CP pack */
    cp_addr += le32_to_cpu(cp_block->cp_pack_total_block_count) - 1;
    if (read_structure_blk(fd, cp_addr, cp_block_2, 1)) {
        goto invalid_cp2;
    }

    cp_block = (struct f2fs_checkpoint*)cp_block_2;

    cp2_version = le64_to_cpu(cp_block->checkpoint_ver);

    if (cp2_version == cp1_version) {
        *version = cp2_version;
        free(cp_block_2);
        return (struct f2fs_checkpoint*)cp_block_1;
    }

    /* There must be something wrong with this checkpoint */
invalid_cp2:
    free(cp_block_2);
invalid_cp1:
    free(cp_block_1);
    return NULL;
}

int get_valid_checkpoint_info(int fd, struct f2fs_super_block* sb, struct f2fs_checkpoint** cp,
                              struct f2fs_info* info) {
    struct f2fs_checkpoint *cp1, *cp2, *cur_cp;
    unsigned long blk_size;
    unsigned long long cp1_version = 0, cp2_version = 0;
    unsigned long long cp1_start_blk_no;
    unsigned long long cp2_start_blk_no;

    blk_size = 1U << le32_to_cpu(sb->log_blocksize);

    /*
     * Find valid cp by reading both packs and finding most recent one.
     */
    cp1_start_blk_no = le32_to_cpu(sb->cp_blkaddr);
    cp1 = validate_checkpoint(cp1_start_blk_no, &cp1_version, fd);

    /* The second checkpoint pack should start at the next segment */
    cp2_start_blk_no = cp1_start_blk_no + (1 << le32_to_cpu(sb->log_blocks_per_seg));
    cp2 = validate_checkpoint(cp2_start_blk_no, &cp2_version, fd);

    if (cp1 && cp2) {
        if (ver_after(cp2_version, cp1_version)) {
            cur_cp = cp2;
            info->cp_valid_cp_blkaddr = cp2_start_blk_no;
            free(cp1);
        } else {
            cur_cp = cp1;
            info->cp_valid_cp_blkaddr = cp1_start_blk_no;
            free(cp2);
        }
    } else if (cp1) {
        cur_cp = cp1;
        info->cp_valid_cp_blkaddr = cp1_start_blk_no;
    } else if (cp2) {
        cur_cp = cp2;
        info->cp_valid_cp_blkaddr = cp2_start_blk_no;
    } else {
        goto fail_no_cp;
    }

    *cp = cur_cp;

    return 0;

fail_no_cp:
    SLOGE("Valid Checkpoint not found!!");
    return -EINVAL;
}

static int gather_sit_info(int fd, struct f2fs_info* info) {
    uint64_t num_segments =
            (info->total_blocks - info->main_blkaddr + info->blocks_per_segment - 1) /
            info->blocks_per_segment;
    uint64_t num_sit_blocks = (num_segments + SIT_ENTRY_PER_BLOCK - 1) / SIT_ENTRY_PER_BLOCK;
    uint64_t sit_block;

    info->sit_blocks = malloc(num_sit_blocks * sizeof(struct f2fs_sit_block));
    if (!info->sit_blocks) return -1;

    for (sit_block = 0; sit_block < num_sit_blocks; sit_block++) {
        off64_t address = info->sit_blkaddr + sit_block;

        if (f2fs_test_bit(sit_block, info->sit_bmp)) address += info->blocks_per_sit;

        SLOGV("Reading cache block starting at block %" PRIu64, address);
        if (read_structure(fd, address * F2FS_BLKSIZE, &info->sit_blocks[sit_block],
                           sizeof(struct f2fs_sit_block))) {
            SLOGE("Could not read sit block at block %" PRIu64, address);
            free(info->sit_blocks);
            info->sit_blocks = NULL;
            return -1;
        }
    }
    return 0;
}

static inline int is_set_ckpt_flags(struct f2fs_checkpoint* cp, unsigned int f) {
    unsigned int ckpt_flags = le32_to_cpu(cp->ckpt_flags);
    return !!(ckpt_flags & f);
}

static inline uint64_t sum_blk_addr(struct f2fs_checkpoint* cp, struct f2fs_info* info, int base,
                                    int type) {
    return info->cp_valid_cp_blkaddr + le32_to_cpu(cp->cp_pack_total_block_count) - (base + 1) +
           type;
}

static int get_sit_summary(int fd, struct f2fs_info* info, struct f2fs_checkpoint* cp) {
    char buffer[F2FS_BLKSIZE];

    info->sit_sums = calloc(1, sizeof(struct f2fs_summary_block));
    if (!info->sit_sums) return -1;

    /* CURSEG_COLD_DATA where the journaled SIT entries are. */
    if (is_set_ckpt_flags(cp, CP_COMPACT_SUM_FLAG)) {
        if (read_structure_blk(fd, info->cp_valid_cp_blkaddr + le32_to_cpu(cp->cp_pack_start_sum),
                               buffer, 1))
            return -1;
        memcpy(&info->sit_sums->journal.n_sits, &buffer[SUM_JOURNAL_SIZE], SUM_JOURNAL_SIZE);
    } else {
        uint64_t blk_addr;
        if (is_set_ckpt_flags(cp, CP_UMOUNT_FLAG))
            blk_addr = sum_blk_addr(cp, info, NR_CURSEG_TYPE, CURSEG_COLD_DATA);
        else
            blk_addr = sum_blk_addr(cp, info, NR_CURSEG_DATA_TYPE, CURSEG_COLD_DATA);

        if (read_structure_blk(fd, blk_addr, buffer, 1)) return -1;

        memcpy(info->sit_sums, buffer, sizeof(struct f2fs_summary_block));
    }
    return 0;
}

struct f2fs_info* generate_f2fs_info(int fd) {
    struct f2fs_super_block* sb = NULL;
    struct f2fs_checkpoint* cp = NULL;
    struct f2fs_info* info;

    info = calloc(1, sizeof(*info));
    if (!info) {
        SLOGE("Out of memory!");
        return NULL;
    }

    sb = malloc(sizeof(*sb));
    if (!sb) {
        SLOGE("Out of memory!");
        free(info);
        return NULL;
    }
    if (read_f2fs_sb(fd, sb)) {
        SLOGE("Failed to read superblock");
        free(info);
        free(sb);
        return NULL;
    }
    dbg_print_raw_sb_info(sb);

    info->cp_blkaddr = le32_to_cpu(sb->cp_blkaddr);
    info->sit_blkaddr = le32_to_cpu(sb->sit_blkaddr);
    info->nat_blkaddr = le32_to_cpu(sb->nat_blkaddr);
    info->ssa_blkaddr = le32_to_cpu(sb->ssa_blkaddr);
    info->main_blkaddr = le32_to_cpu(sb->main_blkaddr);
    info->block_size = F2FS_BLKSIZE;
    info->total_blocks = sb->block_count;
    info->blocks_per_sit = (le32_to_cpu(sb->segment_count_sit) >> 1)
                           << le32_to_cpu(sb->log_blocks_per_seg);
    info->blocks_per_segment = 1U << le32_to_cpu(sb->log_blocks_per_seg);

    if (get_valid_checkpoint_info(fd, sb, &cp, info)) goto error;
    dbg_print_raw_ckpt_struct(cp);

    info->total_user_used = le32_to_cpu(cp->valid_block_count);

    u32 bmp_size = le32_to_cpu(cp->sit_ver_bitmap_bytesize);

    /* get sit validity bitmap */
    info->sit_bmp = malloc(bmp_size);
    if (!info->sit_bmp) {
        SLOGE("Out of memory!");
        goto error;
    }

    info->sit_bmp_size = bmp_size;
    if (read_structure(fd,
                       info->cp_valid_cp_blkaddr * F2FS_BLKSIZE +
                               offsetof(struct f2fs_checkpoint, sit_nat_version_bitmap),
                       info->sit_bmp, bmp_size)) {
        SLOGE("Error getting SIT validity bitmap");
        goto error;
    }

    if (gather_sit_info(fd, info)) {
        SLOGE("Error getting SIT information");
        goto error;
    }
    if (get_sit_summary(fd, info, cp)) {
        SLOGE("Error getting SIT entries in summary area");
        goto error;
    }
    dbg_print_info_struct(info);
    return info;
error:
    free(sb);
    free(cp);
    free_f2fs_info(info);
    return NULL;
}

void free_f2fs_info(struct f2fs_info* info) {
    if (info) {
        free(info->sit_blocks);
        info->sit_blocks = NULL;

        free(info->sit_bmp);
        info->sit_bmp = NULL;

        free(info->sit_sums);
        info->sit_sums = NULL;
    }
    free(info);
}

uint64_t get_num_blocks_used(struct f2fs_info* info) {
    return info->main_blkaddr + info->total_user_used;
}

int f2fs_test_bit(unsigned int nr, const char* p) {
    int mask;
    char* addr = (char*)p;

    addr += (nr >> 3);
    mask = 1 << (7 - (nr & 0x07));
    return (mask & *addr) != 0;
}

int run_on_used_blocks(uint64_t startblock, struct f2fs_info* info,
                       int (*func)(uint64_t pos, void* data), void* data) {
    struct f2fs_sit_entry* sit_entry;
    uint64_t sit_block_num_cur = 0, segnum = 0, block_offset;
    uint64_t block;
    unsigned int used, found, i;

    block = startblock;
    while (block < info->total_blocks) {
        /* TODO: Save only relevant portions of metadata */
        if (block < info->main_blkaddr) {
            if (func(block, data)) {
                SLOGI("func error");
                return -1;
            }
        } else {
            /* Main Section */
            segnum = (block - info->main_blkaddr) / info->blocks_per_segment;

            /* check the SIT entries in the journal */
            found = 0;
            for (i = 0; i < le16_to_cpu(info->sit_sums->journal.n_sits); i++) {
                if (le32_to_cpu(segno_in_journal(&info->sit_sums->journal, i)) == segnum) {
                    sit_entry = &sit_in_journal(&info->sit_sums->journal, i);
                    found = 1;
                    break;
                }
            }

            /* get SIT entry from SIT section */
            if (!found) {
                sit_block_num_cur = segnum / SIT_ENTRY_PER_BLOCK;
                sit_entry =
                        &info->sit_blocks[sit_block_num_cur].entries[segnum % SIT_ENTRY_PER_BLOCK];
            }

            block_offset = (block - info->main_blkaddr) % info->blocks_per_segment;

            if (block_offset == 0 && GET_SIT_VBLOCKS(sit_entry) == 0) {
                block += info->blocks_per_segment;
                continue;
            }

            used = f2fs_test_bit(block_offset, (char*)sit_entry->valid_map);
            if (used)
                if (func(block, data)) return -1;
        }

        block++;
    }
    return 0;
}

struct privdata {
    int count;
    int infd;
    int outfd;
    char* buf;
    char* zbuf;
    int done;
    struct f2fs_info* info;
};

/*
 * This is a simple test program. It performs a block to block copy of a
 * filesystem, replacing blocks identified as unused with 0's.
 */

int copy_used(uint64_t pos, void* data) {
    struct privdata* d = data;
    char* buf;
    int pdone = (pos * 100) / d->info->total_blocks;
    if (pdone > d->done) {
        d->done = pdone;
        printf("Done with %d percent\n", d->done);
    }

    d->count++;
    buf = d->buf;
    if (read_structure_blk(d->infd, (unsigned long long)pos, d->buf, 1)) {
        printf("Error reading!!!\n");
        return -1;
    }

    off64_t ret;
    ret = lseek64(d->outfd, pos * F2FS_BLKSIZE, SEEK_SET);
    if (ret < 0) {
        SLOGE("failed to seek\n");
        return ret;
    }

    ret = write(d->outfd, d->buf, F2FS_BLKSIZE);
    if (ret < 0) {
        SLOGE("failed to write\n");
        return ret;
    }
    if (ret != F2FS_BLKSIZE) {
        SLOGE("failed to read all\n");
        return -1;
    }
    return 0;
}

int main(int argc, char** argv) {
    if (argc != 3) printf("Usage: %s fs_file_in fs_file_out\n", argv[0]);
    char* in = argv[1];
    char* out = argv[2];
    int infd, outfd;

    if ((infd = open(in, O_RDONLY)) < 0) {
        SLOGE("Cannot open device");
        return 0;
    }
    if ((outfd = open(out, O_WRONLY | O_CREAT, S_IRUSR | S_IWUSR)) < 0) {
        SLOGE("Cannot open output");
        return 0;
    }

    struct privdata d;
    d.infd = infd;
    d.outfd = outfd;
    d.count = 0;
    struct f2fs_info* info = generate_f2fs_info(infd);
    if (!info) {
        printf("Failed to generate info!");
        return -1;
    }
    char* buf = malloc(F2FS_BLKSIZE);
    char* zbuf = calloc(1, F2FS_BLKSIZE);
    d.buf = buf;
    d.zbuf = zbuf;
    d.done = 0;
    d.info = info;
    int expected_count = get_num_blocks_used(info);
    run_on_used_blocks(0, info, &copy_used, &d);
    printf("Copied %d blocks. Expected to copy %d\n", d.count, expected_count);
    ftruncate64(outfd, info->total_blocks * F2FS_BLKSIZE);
    free_f2fs_info(info);
    free(buf);
    free(zbuf);
    close(infd);
    close(outfd);
    return 0;
}
