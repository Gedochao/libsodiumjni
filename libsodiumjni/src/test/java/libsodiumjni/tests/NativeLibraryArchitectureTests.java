package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Test;

/**
 * Checks that the native library bundled for the current platform targets the CPU architecture of
 * the current JVM, by reading its ELF / Mach-O / PE header.
 */
public class NativeLibraryArchitectureTests {

    @Test
    public void bundledLibraryMatchesJvmArchitecture() throws IOException {
        String resourcePath = LoadLibrary.resourcePath();
        URL url = NativeLibraryArchitectureTests.class.getClassLoader().getResource(resourcePath);
        assertNotNull("Resource " + resourcePath + " not found", url);

        byte[] header;
        try (InputStream is = url.openStream()) {
            header = is.readNBytes(4096);
        }
        assertEquals(
                "Architecture of " + resourcePath,
                jvmArchitecture(),
                libraryArchitecture(header));
    }

    private static String jvmArchitecture() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        switch (arch) {
            case "amd64":
            case "x86_64":
                return "x86_64";
            case "aarch64":
            case "arm64":
                return "aarch64";
            default:
                return arch;
        }
    }

    private static String libraryArchitecture(byte[] header) {
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        // ELF: e_machine at offset 18
        if (header.length >= 20 && header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
            int machine = Short.toUnsignedInt(buf.getShort(18));
            switch (machine) {
                case 62: return "x86_64";
                case 183: return "aarch64";
                default: return "unknown ELF machine " + machine;
            }
        }

        // 64-bit Mach-O (little endian): cputype at offset 4
        if (header.length >= 8 && buf.getInt(0) == 0xfeedfacf) {
            int cpuType = buf.getInt(4);
            switch (cpuType) {
                case 0x01000007: return "x86_64";
                case 0x0100000c: return "aarch64";
                default: return "unknown Mach-O CPU type " + Integer.toHexString(cpuType);
            }
        }

        // PE: offset of the "PE\0\0" signature at 0x3c, followed by the Machine field
        if (header.length >= 0x40 && header[0] == 'M' && header[1] == 'Z') {
            int peOffset = buf.getInt(0x3c);
            if (peOffset + 6 <= header.length && buf.getInt(peOffset) == 0x00004550) {
                int machine = Short.toUnsignedInt(buf.getShort(peOffset + 4));
                switch (machine) {
                    case 0x8664: return "x86_64";
                    case 0xaa64: return "aarch64";
                    default: return "unknown PE machine " + Integer.toHexString(machine);
                }
            }
        }

        return "unrecognized binary format";
    }
}
