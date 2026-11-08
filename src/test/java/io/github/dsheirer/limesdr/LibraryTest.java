package io.github.dsheirer.limesdr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.limesdr.internal.Layouts;
import io.github.dsheirer.limesdr.internal.Native;
import io.github.dsheirer.limesdr.internal.NativeLibraryLoader;
import io.github.dsheirer.libusb.NativeLoader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests that need the native library for the current platform but no hardware. */
class LibraryTest {
    @Test
    void structureLayoutsMatchHeader() {
        assertEquals(24, Layouts.RANGE.byteSize());
        assertEquals(168, Layouts.DEVICE_INFO.byteSize());
        assertEquals(112, Layouts.offset(Layouts.DEVICE_INFO, "boardSerialNumber"));
        assertEquals(32, Layouts.STREAM.byteSize());
        assertEquals(12, Layouts.offset(Layouts.STREAM, "channel"));
        assertEquals(24, Layouts.offset(Layouts.STREAM, "dataFmt"));
        assertEquals(16, Layouts.STREAM_META.byteSize());
        assertEquals(48, Layouts.STREAM_STATUS.byteSize());
        assertEquals(32, Layouts.offset(Layouts.STREAM_STATUS, "linkRate"));
        assertEquals(24, Layouts.LMS7_PARAMETER.byteSize());
        assertEquals(8, Layouts.offset(Layouts.LMS7_PARAMETER, "name"));
    }

    @Test
    void loadsLibraryAndResolvesEveryFunction() {
        assertTrue(LimeSdr.libraryVersion().matches("\\d+\\.\\d+\\.\\d+.*"), LimeSdr.libraryVersion());
        assertNotNull(Native.PROGRAM);
    }

    @Test
    void reportsTheErrorThatTheLibraryLogged() {
        List<String> messages = new ArrayList<>();
        LimeSdr.setLogHandler((level, message) -> {
            if (level == LogLevel.ERROR) {
                messages.add(message);
            }
        });
        try {
            LimeSdrException e = assertThrows(LimeSdrException.class,
                () -> LimeSdr.call("LMS_Init", Native.INIT, MemorySegment.NULL));
            assertEquals(LimeSdrError.ERROR, e.error());
            assertTrue(e.getMessage().startsWith("LMS_Init failed: ERROR (-1): "), e.getMessage());
            assertTrue(e.getMessage().contains("NULL"), e.getMessage());
            assertEquals(1, messages.size(), messages.toString());
            // The next failure reports only its own error.
            e = assertThrows(LimeSdrException.class,
                () -> LimeSdr.call("LMS_Close", Native.CLOSE, MemorySegment.NULL));
            assertEquals(1, e.getMessage().split("NULL", -1).length - 1, e.getMessage());
        } finally {
            LimeSdr.setLogHandler(null);
        }
    }

    @Test
    void listsDevicesWithoutHardware() throws LimeSdrException {
        // Passes with or without a device; it checks enumeration does not fail.
        for (DeviceEntry entry : LimeSdr.listDevices()) {
            assertTrue(entry.descriptor().startsWith(entry.name()));
        }
    }

    @Test
    void decodesErrors() {
        assertEquals(LimeSdrError.TIMEOUT, LimeSdrError.fromCode(-8));
        assertEquals(LimeSdrError.UNKNOWN, LimeSdrError.fromCode(-99));
        assertEquals("LMS_Open failed: NOT_CONNECTED (-12): gone",
            new LimeSdrException("LMS_Open", -12, "gone").getMessage());
        assertEquals("LMS_Open failed: UNKNOWN (-99)", new LimeSdrException("LMS_Open", -99, null).getMessage());
    }

    @Test
    void parsesDeviceDescriptors() {
        DeviceEntry entry = DeviceEntry.parse("LimeSDR-USB, media=USB3.0, addr=1d50:6108, serial=0009060B00462008");
        assertEquals("LimeSDR-USB", entry.name());
        assertEquals("USB3.0", entry.media());
        assertEquals("1d50:6108", entry.address());
        assertEquals("0009060B00462008", entry.serialNumber());
        assertTrue(entry.hasSerialNumber("9060b00462008"));
        assertTrue(entry.hasSerialNumber("0x9060B00462008".substring(2)));
        DeviceEntry bare = DeviceEntry.parse("LimeSDR XTRX, media=PCIe");
        assertEquals("LimeSDR XTRX", bare.name());
        assertEquals("PCIe", bare.media());
        assertEquals("", bare.serialNumber());
        assertEquals(false, bare.hasSerialNumber("0"));
    }

    @Test
    void namesLibraries() {
        assertEquals("liblimesuiteng-legacyapi.so", NativeLibraryLoader.libraryName("linux-aarch64"));
        assertEquals("liblimesuiteng-legacyapi.dylib", NativeLibraryLoader.libraryName("macos-x86_64"));
        assertEquals("limesuiteng-legacyapi.dll", NativeLibraryLoader.libraryName("windows-x86_64"));
    }

    @Test
    void sharesLibusbWithJlibusb() throws Throwable {
        // Loading LimeSuiteNG loads jlibusb's libusb, so both sit in its directory.
        assertNotNull(LimeSdr.libraryVersion());
        Path directory = NativeLoader.directory();
        String platform = NativeLoader.platform();
        assertTrue(Files.isRegularFile(directory.resolve(NativeLoader.libusbName(platform))));
        assertTrue(Files.isRegularFile(directory.resolve(NativeLibraryLoader.libraryName(platform))));
        if (platform.startsWith("linux")) {
            // One copy of libusb in the process, the one jlibusb extracted.
            List<String> mapped = Files.readAllLines(Path.of("/proc/self/maps")).stream()
                .filter(line -> line.contains("libusb-1.0"))
                .map(line -> line.substring(line.indexOf('/')))
                .distinct()
                .toList();
            assertEquals(List.of(directory.resolve("libusb-1.0.so.0").toString()), mapped);
        } else if (platform.startsWith("macos")) {
            // dyld must reuse the libusb jlibusb loaded when LimeSuiteNG asks for
            // @loader_path/libusb-1.0.0.dylib, even through the /var symlink.
            List<String> images = dyldImages().stream().filter(name -> name.contains("libusb-1.0")).toList();
            assertEquals(1, images.size(), images.toString());
        }
    }

    /** Paths of the images dyld has loaded into this process. */
    private static List<String> dyldImages() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup system = linker.defaultLookup();
        MethodHandle count = linker.downcallHandle(system.find("_dyld_image_count").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        MethodHandle name = linker.downcallHandle(system.find("_dyld_get_image_name").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        int images = (int) count.invokeExact();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < images; i++) {
            MemorySegment pointer = (MemorySegment) name.invokeExact(i);
            if (!pointer.equals(MemorySegment.NULL)) {
                names.add(pointer.reinterpret(Long.MAX_VALUE).getString(0));
            }
        }
        return names;
    }

    @Test
    void convertsSampleBuffers() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment shorts = arena.allocateFrom(ValueLayout.JAVA_SHORT, (short) 1024, (short) -2048,
                (short) 2047, (short) 0);
            SampleBuffer i12 = new SampleBuffer(0, SampleFormat.I12, shorts, 7);
            assertEquals(2, i12.sampleCount());
            assertArrayEquals(new short[] {1024, -2048, 2047, 0}, i12.copyShorts());
            assertArrayEquals(new float[] {0.5f, -1f, 2047 / 2048f, 0f}, i12.copyFloats());
            MemorySegment floats = arena.allocateFrom(ValueLayout.JAVA_FLOAT, 0.25f, -0.5f);
            SampleBuffer f32 = new SampleBuffer(1, SampleFormat.F32, floats, 0);
            assertEquals(1, f32.sampleCount());
            assertArrayEquals(new float[] {0.25f, -0.5f}, f32.copyFloats());
            assertThrows(IllegalStateException.class, f32::copyShorts);
        }
    }

    @Test
    void validatesSettings() {
        assertThrows(IllegalArgumentException.class, () -> StreamConfig.of(-1, SampleFormat.I16));
        assertThrows(IllegalArgumentException.class, () -> StreamConfig.of(0, SampleFormat.I16).withFifoSize(0));
        assertEquals(1f, StreamConfig.of(0, SampleFormat.I16).withThroughputVsLatency(1).throughputVsLatency());
        assertThrows(IllegalArgumentException.class,
            () -> StreamConfig.of(0, SampleFormat.I16).withThroughputVsLatency(1.5f));
        assertEquals(15, Lms7Parameter.G_LNA_RFE.maxValue());
        assertEquals(31, Lms7Parameter.G_PGA_RBB.maxValue());
        assertThrows(IllegalArgumentException.class, () -> new Lms7Parameter(0x10000, 0, 0, 0, "X"));
        assertThrows(IllegalArgumentException.class, () -> new Lms7Parameter(0x20, 1, 2, 0, "X"));
        assertEquals(TestSignal.DC, TestSignal.fromCode(5));
        assertEquals(LogLevel.DEBUG, LogLevel.fromCode(42));
    }
}
