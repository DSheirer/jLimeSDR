package io.github.dsheirer.limesdr;

import static java.lang.foreign.ValueLayout.ADDRESS;

import io.github.dsheirer.limesdr.internal.Layouts;
import io.github.dsheirer.limesdr.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entry point of the library: finds and opens LimeSDR devices.
 *
 * <pre>{@code
 * try (LimeSdrDevice device = LimeSdr.open()) {
 *     device.init();
 *     device.enableChannel(Direction.RX, 0, true);
 *     device.setSampleRate(10e6, 0);
 *     device.setFrequency(Direction.RX, 0, 100e6);
 *     device.setAntenna(Direction.RX, 0, LimeSdrDevice.PATH_LNAW);
 *     device.setGain(Direction.RX, 0, 40);
 *     device.setLpfBandwidth(Direction.RX, 0, 10e6);
 *     device.calibrate(Direction.RX, 0, 10e6);
 *     device.startRx(StreamConfig.of(0, SampleFormat.I16), 65536, buffer -> {
 *         process(buffer.copyShorts());
 *         return true;
 *     });
 *     Thread.sleep(1000);
 * }
 * }</pre>
 *
 * <p>LimeSuiteNG's log messages go to the {@link System.Logger} named
 * {@value #LOGGER_NAME} unless {@link #setLogHandler} replaces it.
 */
public final class LimeSdr {
    /** Name of the {@link System.Logger} that receives LimeSuiteNG's messages by default. */
    public static final String LOGGER_NAME = "io.github.dsheirer.limesdr.LimeSuiteNG";

    static final Object LOCK = new Object();

    private static final System.Logger LOGGER = System.getLogger(LOGGER_NAME);
    private static final LogHandler DEFAULT_HANDLER =
        (level, message) -> LOGGER.log(level.systemLevel(), message);

    // The library reports why a function failed by logging an error on the
    // calling thread just before it returns, so the last one is kept per thread.
    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

    private static volatile LogHandler logHandler = DEFAULT_HANDLER;

    static {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(LimeSdr.class, "onLog",
                MethodType.methodType(void.class, int.class, MemorySegment.class));
            // The global arena keeps the stub alive as long as the library may call it.
            Native.invokeVoid(Native.REGISTER_LOG_HANDLER, Native.logHandler(target, Arena.global()));
            // The library logs from static destructors, which run on the JVM's
            // exit path where an upcall aborts the process. Its own handler
            // takes over before then.
            Runtime.getRuntime().addShutdownHook(new Thread(
                () -> Native.invokeVoid(Native.REGISTER_LOG_HANDLER, MemorySegment.NULL), "jlimesdr-log-reset"));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private LimeSdr() {
    }

    /** Returns the version of the loaded native library, such as {@code 0.3.0-g6c3feee9}. */
    public static String libraryVersion() {
        return Native.invokeString(Native.GET_LIBRARY_VERSION);
    }

    /**
     * Replaces the handler of LimeSuiteNG's log messages. Null restores the
     * default, which logs to the {@link System.Logger} named {@value #LOGGER_NAME}.
     */
    public static void setLogHandler(LogHandler handler) {
        logHandler = handler == null ? DEFAULT_HANDLER : handler;
    }

    /**
     * Returns the error message the library last recorded on this thread, from
     * {@code LMS_GetLastErrorMessage}. Upstream deprecates it: it is not cleared
     * on success and only some errors set it. {@link LimeSdrException} messages
     * carry the errors logged by the failing call instead.
     */
    public static String lastErrorMessage() {
        return Native.invokeString(Native.GET_LAST_ERROR_MESSAGE);
    }

    /** Lists the connected devices. */
    public static List<DeviceEntry> listDevices() throws LimeSdrException {
        synchronized (LOCK) {
            for (int attempt = 0; attempt < 3; attempt++) {
                int count = call("LMS_GetDeviceList", Native.GET_DEVICE_LIST, MemorySegment.NULL);
                if (count == 0) {
                    return List.of();
                }
                // LMS_GetDeviceList writes one entry per device it finds without
                // a size limit, so the list leaves room for devices connected
                // between the two calls.
                int capacity = count * 2 + 8;
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment list = arena.allocate(Layouts.INFO_STRING_SIZE * capacity);
                    int found = call("LMS_GetDeviceList", Native.GET_DEVICE_LIST, list);
                    if (found > capacity) {
                        continue;
                    }
                    List<DeviceEntry> devices = new ArrayList<>(found);
                    for (int i = 0; i < found; i++) {
                        String descriptor = Native.string(list, i * Layouts.INFO_STRING_SIZE, Layouts.INFO_STRING_SIZE);
                        devices.add(DeviceEntry.parse(descriptor));
                    }
                    return List.copyOf(devices);
                }
            }
            throw new LimeSdrException("LMS_GetDeviceList", LimeSdrError.BUSY.code(), "the device list kept changing");
        }
    }

    /** Opens the first device found. */
    public static LimeSdrDevice open() throws LimeSdrException {
        return openDescriptor(null);
    }

    /** Opens a device from {@link #listDevices()}. */
    public static LimeSdrDevice open(DeviceEntry entry) throws LimeSdrException {
        Objects.requireNonNull(entry, "entry");
        return openDescriptor(entry.descriptor());
    }

    /**
     * Opens the device with a serial number, as {@link DeviceEntry#serialNumber()}
     * reports it. Case and leading zeros do not matter.
     */
    public static LimeSdrDevice open(String serialNumber) throws LimeSdrException {
        Objects.requireNonNull(serialNumber, "serialNumber");
        if (serialNumber.isBlank()) {
            throw new IllegalArgumentException("Serial number is empty");
        }
        synchronized (LOCK) {
            for (DeviceEntry entry : listDevices()) {
                if (entry.hasSerialNumber(serialNumber)) {
                    return openDescriptor(entry.descriptor());
                }
            }
        }
        throw new LimeSdrException("LMS_Open", LimeSdrError.ERROR.code(), "no device with serial number " + serialNumber);
    }

    private static LimeSdrDevice openDescriptor(String descriptor) throws LimeSdrException {
        synchronized (LOCK) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment info = MemorySegment.NULL;
                if (descriptor != null) {
                    byte[] bytes = descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (bytes.length >= Layouts.INFO_STRING_SIZE) {
                        throw new IllegalArgumentException("Device descriptor too long: " + descriptor);
                    }
                    info = arena.allocate(Layouts.INFO_STRING_SIZE);
                    MemorySegment.copy(bytes, 0, info, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, bytes.length);
                }
                MemorySegment pointer = arena.allocate(ADDRESS);
                pointer.set(ADDRESS, 0, MemorySegment.NULL);
                call("LMS_Open", Native.OPEN, pointer, info, MemorySegment.NULL);
                return new LimeSdrDevice(pointer.get(ADDRESS, 0));
            }
        }
    }

    /**
     * Calls a native function returning a status or count, and throws if it is
     * negative. Errors logged during the call become the exception's message.
     */
    static int call(String function, MethodHandle handle, Object... arguments) throws LimeSdrException {
        LAST_ERROR.remove();
        int result = Native.invoke(handle, arguments);
        if (result < 0) {
            throw failure(function, result);
        }
        return result;
    }

    /** Clears the error recorded on this thread before a call checked with {@link #failure}. */
    static void clearError() {
        LAST_ERROR.remove();
    }

    /** Creates the exception for a failed call, with the error it logged. */
    static LimeSdrException failure(String function, int result) {
        String message = LAST_ERROR.get();
        LAST_ERROR.remove();
        return new LimeSdrException(function, result, message);
    }

    // Called by the library, on any thread. Nothing may be thrown back into it.
    private static void onLog(int level, MemorySegment message) {
        try {
            LogLevel logLevel = LogLevel.fromCode(level);
            String text = Native.string(message);
            if (text == null) {
                text = "";
            }
            if (logLevel == LogLevel.ERROR || logLevel == LogLevel.CRITICAL) {
                String previous = LAST_ERROR.get();
                // Threads that are never checked, such as a receive loop, must not grow it forever.
                boolean append = previous != null && previous.length() < 1000;
                LAST_ERROR.set(append ? previous + "; " + text : text);
            }
            logHandler.log(logLevel, text);
        } catch (Throwable t) {
            // A handler failure must not reach native code.
        }
    }
}
