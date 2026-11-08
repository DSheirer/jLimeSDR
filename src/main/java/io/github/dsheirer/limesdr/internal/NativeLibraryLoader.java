package io.github.dsheirer.limesdr.internal;

import io.github.dsheirer.libusb.NativeLoader;
import java.lang.foreign.SymbolLookup;
import java.util.List;

/**
 * Loads the LimeSuiteNG legacy API through jlibusb, which supplies the libusb
 * it depends on.
 *
 * <p>By default the library is extracted from the jar, next to jlibusb's copy
 * of libusb. Set the {@value #PATH_PROPERTY} system property to a directory to
 * load it from there instead; set {@code jlibusb.library.path} to replace
 * libusb. Windows uses the Cypress and FTDI drivers instead of libusb, both
 * linked into the one DLL, so libusb is loaded there but not used.
 */
public final class NativeLibraryLoader {
    /** System property naming a directory that overrides the bundled LimeSuiteNG library. */
    public static final String PATH_PROPERTY = "jlimesdr.library.path";

    private static final String RESOURCE_ROOT = "/jlimesdr/native/";

    private NativeLibraryLoader() {
    }

    /** File name of the LimeSuiteNG legacy API library on a platform, such as {@code linux-x86_64}. */
    public static String libraryName(String platform) {
        if (platform.startsWith("linux")) {
            return "liblimesuiteng-legacyapi.so";
        }
        if (platform.startsWith("macos")) {
            return "liblimesuiteng-legacyapi.dylib";
        }
        return "limesuiteng-legacyapi.dll";
    }

    /** Loads libusb and the LimeSuiteNG legacy API and returns a lookup for the symbols of the latter. */
    static SymbolLookup load() {
        String name = libraryName(NativeLoader.platform());
        return NativeLoader.load(NativeLibraryLoader.class, RESOURCE_ROOT, List.of(name), PATH_PROPERTY);
    }
}
