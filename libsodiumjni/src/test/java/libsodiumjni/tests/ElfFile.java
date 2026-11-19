package libsodiumjni.tests;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reads the hardening-related properties of 64-bit little-endian ELF files (Linux shared
 * libraries), as {@code readelf -lW} / {@code readelf -d} would show them.
 */
final class ElfFile {

    private ElfFile() {}

    private static final int PT_DYNAMIC = 2;
    private static final int PT_GNU_STACK = 0x6474e551;
    private static final int PT_GNU_RELRO = 0x6474e552;
    private static final int PF_X = 1;

    private static final long DT_NULL = 0;
    private static final long DT_BIND_NOW = 24;
    private static final long DT_FLAGS = 30;
    private static final long DT_FLAGS_1 = 0x6ffffffbL;
    private static final long DF_BIND_NOW = 0x8;
    private static final long DF_1_NOW = 0x1;

    static final class Hardening {
        /** GNU_RELRO program header (relocations made read-only once loaded) */
        final boolean relro;
        /** symbols bound at load time (BIND_NOW / NOW flag), which makes RELRO cover the GOT ("full RELRO") */
        final boolean bindNow;
        /** GNU_STACK program header, if any (without it, the stack is executable) */
        final boolean hasStackHeader;
        /** GNU_STACK has the executable flag */
        final boolean executableStack;

        Hardening(boolean relro, boolean bindNow, boolean hasStackHeader, boolean executableStack) {
            this.relro = relro;
            this.bindNow = bindNow;
            this.hasStackHeader = hasStackHeader;
            this.executableStack = executableStack;
        }
    }

    static Hardening hardening(byte[] content) {
        if (content.length < 64 || content[0] != 0x7f || content[1] != 'E' || content[2] != 'L' || content[3] != 'F')
            throw new IllegalArgumentException("Not an ELF file");
        if (content[4] != 2 || content[5] != 1)
            throw new IllegalArgumentException("Not a 64-bit little-endian ELF file");
        ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        long phoff = buf.getLong(0x20);
        int phentsize = Short.toUnsignedInt(buf.getShort(0x36));
        int phnum = Short.toUnsignedInt(buf.getShort(0x38));

        boolean relro = false, bindNow = false, hasStackHeader = false, executableStack = false;
        for (int i = 0; i < phnum; i++) {
            int ph = Math.toIntExact(phoff + (long) i * phentsize);
            int type = buf.getInt(ph);
            int flags = buf.getInt(ph + 4);
            if (type == PT_GNU_RELRO) relro = true;
            else if (type == PT_GNU_STACK) {
                hasStackHeader = true;
                executableStack = (flags & PF_X) != 0;
            } else if (type == PT_DYNAMIC) {
                int offset = Math.toIntExact(buf.getLong(ph + 8));
                int size = Math.toIntExact(buf.getLong(ph + 32));
                // Elf64_Dyn entries: d_tag, d_val (8 bytes each), up to DT_NULL
                for (int entry = offset; entry + 16 <= offset + size; entry += 16) {
                    long tag = buf.getLong(entry);
                    long value = buf.getLong(entry + 8);
                    if (tag == DT_NULL) break;
                    if (tag == DT_BIND_NOW
                            || (tag == DT_FLAGS && (value & DF_BIND_NOW) != 0)
                            || (tag == DT_FLAGS_1 && (value & DF_1_NOW) != 0))
                        bindNow = true;
                }
            }
        }
        return new Hardening(relro, bindNow, hasStackHeader, executableStack);
    }
}
