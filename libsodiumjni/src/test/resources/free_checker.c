/*
 * Test helper, preloaded in a child JVM by NativeMemoryWipingTests (LD_PRELOAD on Linux,
 * DYLD_INSERT_LIBRARIES on macOS): wraps free(), and reports freed blocks that still contain the
 * secret or control marker bytes passed via environment variables.
 *
 *   FREE_CHECKER_SECRET   hex-encoded bytes that must never be found in freed memory
 *   FREE_CHECKER_CONTROL  hex-encoded bytes deliberately left in freed memory by the child JVM, to
 *                         check that this checker actually works
 *   FREE_CHECKER_OUT      file where "ACTIVE", "LEAK secret" and "LEAK control" lines are written
 *
 * Must not call malloc / free itself (directly or not), hence getenv, open and write only.
 */

#define _GNU_SOURCE
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#ifdef __APPLE__
#include <malloc/malloc.h>
#define usable_size(p) malloc_size(p)
#else
#include <dlfcn.h>
#include <malloc.h>
#define usable_size(p) malloc_usable_size(p)
#endif

#define MAX_MARKER_LEN 256

static unsigned char secret[MAX_MARKER_LEN];
static size_t secret_len = 0;
static unsigned char control[MAX_MARKER_LEN];
static size_t control_len = 0;
static int out_fd = -1;

static int hex_value(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static size_t parse_hex(const char *hex, unsigned char *dest) {
    size_t len = 0;
    if (hex == NULL) return 0;
    while (hex[0] != '\0' && hex[1] != '\0' && len < MAX_MARKER_LEN) {
        int hi = hex_value(hex[0]), lo = hex_value(hex[1]);
        if (hi < 0 || lo < 0) return 0;
        dest[len++] = (unsigned char) (hi * 16 + lo);
        hex += 2;
    }
    return len;
}

static void report(const char *line) {
    if (out_fd >= 0) {
        ssize_t ignored = write(out_fd, line, strlen(line));
        (void) ignored;
    }
}

static void check_block(void *ptr) {
    if (ptr == NULL || out_fd < 0) return;
    size_t size = usable_size(ptr);
    if (size == 0) return;
    if (secret_len > 0 && memmem(ptr, size, secret, secret_len) != NULL)
        report("LEAK secret\n");
    if (control_len > 0 && memmem(ptr, size, control, control_len) != NULL)
        report("LEAK control\n");
}

#ifdef __APPLE__

static void checking_free(void *ptr) {
    check_block(ptr);
    free(ptr); /* calls the original free: interposing doesn't apply within this library */
}

__attribute__((used)) static const struct {
    const void *replacement;
    const void *replacee;
} interposers[] __attribute__((section("__DATA,__interpose"))) = {
    { (const void *) checking_free, (const void *) free }
};

#else

static void (*real_free)(void *) = NULL;

void free(void *ptr) {
    if (real_free == NULL) {
        /* dlsym can itself call free before we know the real one: leak those, this is a test helper */
        return;
    }
    check_block(ptr);
    real_free(ptr);
}

#endif

__attribute__((constructor)) static void init(void) {
#ifndef __APPLE__
    real_free = (void (*)(void *)) dlsym(RTLD_NEXT, "free");
#endif
    secret_len = parse_hex(getenv("FREE_CHECKER_SECRET"), secret);
    control_len = parse_hex(getenv("FREE_CHECKER_CONTROL"), control);
    const char *out = getenv("FREE_CHECKER_OUT");
    if (out != NULL)
        out_fd = open(out, O_WRONLY | O_CREAT | O_APPEND, 0600);
    report("ACTIVE\n");
}
