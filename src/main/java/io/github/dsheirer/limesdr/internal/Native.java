package io.github.dsheirer.limesdr.internal;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Downcall handles for the functions of the LimeSuiteNG legacy API, {@code LimeSuite.h}.
 *
 * <p>{@code bool} is passed as {@code boolean}, enums and {@code unsigned} as
 * {@code int}, {@code uint8_t} as {@code byte}, {@code uint16_t} and
 * {@code int16_t} as {@code short}, {@code float_type} as {@code double} and
 * {@code size_t} and {@code uint64_t} as {@code long}: every supported platform
 * is 64-bit.
 *
 * <p>{@code native/check-bindings.sh} compares this list against {@code LimeSuite.h}.
 */
public final class Native {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = NativeLibraryLoader.load();

    // ---- Initialization ----
    public static final MethodHandle GET_DEVICE_LIST = call("LMS_GetDeviceList", ADDRESS);
    public static final MethodHandle OPEN = call("LMS_Open", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle CLOSE = call("LMS_Close", ADDRESS);
    public static final MethodHandle INIT = call("LMS_Init", ADDRESS);
    public static final MethodHandle RESET = call("LMS_Reset", ADDRESS);
    public static final MethodHandle GET_LIBRARY_VERSION = pointerCall("LMS_GetLibraryVersion");
    public static final MethodHandle GET_LAST_ERROR_MESSAGE = pointerCall("LMS_GetLastErrorMessage");
    public static final MethodHandle REGISTER_LOG_HANDLER = voidCall("LMS_RegisterLogHandler", ADDRESS);
    public static final MethodHandle GET_DEVICE_INFO = pointerCall("LMS_GetDeviceInfo", ADDRESS);

    // ---- Channels and sample rate ----
    public static final MethodHandle GET_NUM_CHANNELS = call("LMS_GetNumChannels", ADDRESS, JAVA_BOOLEAN);
    public static final MethodHandle ENABLE_CHANNEL =
        call("LMS_EnableChannel", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_BOOLEAN);
    public static final MethodHandle SET_SAMPLE_RATE = call("LMS_SetSampleRate", ADDRESS, JAVA_DOUBLE, JAVA_LONG);
    public static final MethodHandle SET_SAMPLE_RATE_DIR =
        call("LMS_SetSampleRateDir", ADDRESS, JAVA_BOOLEAN, JAVA_DOUBLE, JAVA_LONG);
    public static final MethodHandle GET_SAMPLE_RATE =
        call("LMS_GetSampleRate", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS, ADDRESS);
    public static final MethodHandle GET_SAMPLE_RATE_RANGE =
        call("LMS_GetSampleRateRange", ADDRESS, JAVA_BOOLEAN, ADDRESS);

    // ---- Tuning ----
    public static final MethodHandle SET_LO_FREQUENCY =
        call("LMS_SetLOFrequency", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_DOUBLE);
    public static final MethodHandle GET_LO_FREQUENCY =
        call("LMS_GetLOFrequency", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS);
    public static final MethodHandle GET_LO_FREQUENCY_RANGE =
        call("LMS_GetLOFrequencyRange", ADDRESS, JAVA_BOOLEAN, ADDRESS);

    // ---- Antennas ----
    public static final MethodHandle GET_ANTENNA_LIST =
        call("LMS_GetAntennaList", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS);
    public static final MethodHandle SET_ANTENNA = call("LMS_SetAntenna", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_LONG);
    public static final MethodHandle GET_ANTENNA = call("LMS_GetAntenna", ADDRESS, JAVA_BOOLEAN, JAVA_LONG);
    public static final MethodHandle GET_ANTENNA_BW =
        call("LMS_GetAntennaBW", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_LONG, ADDRESS);

    // ---- Gain ----
    public static final MethodHandle SET_NORMALIZED_GAIN =
        call("LMS_SetNormalizedGain", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_DOUBLE);
    public static final MethodHandle SET_GAIN_DB = call("LMS_SetGaindB", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_INT);
    public static final MethodHandle GET_NORMALIZED_GAIN =
        call("LMS_GetNormalizedGain", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS);
    public static final MethodHandle GET_GAIN_DB = call("LMS_GetGaindB", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS);

    // ---- Filters ----
    public static final MethodHandle SET_LPF_BW = call("LMS_SetLPFBW", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_DOUBLE);
    public static final MethodHandle GET_LPF_BW = call("LMS_GetLPFBW", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS);
    public static final MethodHandle GET_LPF_BW_RANGE = call("LMS_GetLPFBWRange", ADDRESS, JAVA_BOOLEAN, ADDRESS);
    public static final MethodHandle SET_LPF = call("LMS_SetLPF", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_BOOLEAN);
    public static final MethodHandle SET_GFIR_LPF =
        call("LMS_SetGFIRLPF", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_BOOLEAN, JAVA_DOUBLE);
    public static final MethodHandle SET_GFIR_COEFF =
        call("LMS_SetGFIRCoeff", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG);
    public static final MethodHandle GET_GFIR_COEFF =
        call("LMS_GetGFIRCoeff", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, ADDRESS);
    public static final MethodHandle SET_GFIR =
        call("LMS_SetGFIR", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, JAVA_BOOLEAN);

    // ---- Calibration, test signals and configuration files ----
    public static final MethodHandle CALIBRATE =
        call("LMS_Calibrate", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_DOUBLE, JAVA_INT);
    public static final MethodHandle SET_TEST_SIGNAL =
        call("LMS_SetTestSignal", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, JAVA_SHORT, JAVA_SHORT);
    public static final MethodHandle GET_TEST_SIGNAL =
        call("LMS_GetTestSignal", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS);
    public static final MethodHandle LOAD_CONFIG = call("LMS_LoadConfig", ADDRESS, ADDRESS);
    public static final MethodHandle SAVE_CONFIG = call("LMS_SaveConfig", ADDRESS, ADDRESS);
    public static final MethodHandle GET_CHIP_TEMPERATURE = call("LMS_GetChipTemperature", ADDRESS, JAVA_LONG, ADDRESS);

    // ---- NCO ----
    public static final MethodHandle SET_NCO_FREQUENCY =
        call("LMS_SetNCOFrequency", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS, JAVA_DOUBLE);
    public static final MethodHandle GET_NCO_FREQUENCY =
        call("LMS_GetNCOFrequency", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS, ADDRESS);
    public static final MethodHandle SET_NCO_PHASE =
        call("LMS_SetNCOPhase", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS, JAVA_DOUBLE);
    public static final MethodHandle GET_NCO_PHASE =
        call("LMS_GetNCOPhase", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, ADDRESS, ADDRESS);
    public static final MethodHandle SET_NCO_INDEX =
        call("LMS_SetNCOIndex", ADDRESS, JAVA_BOOLEAN, JAVA_LONG, JAVA_INT, JAVA_BOOLEAN);
    public static final MethodHandle GET_NCO_INDEX = call("LMS_GetNCOIndex", ADDRESS, JAVA_BOOLEAN, JAVA_LONG);

    // ---- Registers, parameters and clocks ----
    public static final MethodHandle ENABLE_CACHE = call("LMS_EnableCache", ADDRESS, JAVA_BOOLEAN);
    public static final MethodHandle SYNCHRONIZE = call("LMS_Synchronize", ADDRESS, JAVA_BOOLEAN);
    public static final MethodHandle READ_LMS_REG = call("LMS_ReadLMSReg", ADDRESS, JAVA_INT, ADDRESS);
    public static final MethodHandle WRITE_LMS_REG = call("LMS_WriteLMSReg", ADDRESS, JAVA_INT, JAVA_SHORT);
    public static final MethodHandle READ_PARAM = call("LMS_ReadParam", ADDRESS, Layouts.LMS7_PARAMETER, ADDRESS);
    public static final MethodHandle WRITE_PARAM =
        call("LMS_WriteParam", ADDRESS, Layouts.LMS7_PARAMETER, JAVA_SHORT);
    public static final MethodHandle READ_FPGA_REG = call("LMS_ReadFPGAReg", ADDRESS, JAVA_INT, ADDRESS);
    public static final MethodHandle WRITE_FPGA_REG = call("LMS_WriteFPGAReg", ADDRESS, JAVA_INT, JAVA_SHORT);
    public static final MethodHandle READ_CUSTOM_BOARD_PARAM =
        call("LMS_ReadCustomBoardParam", ADDRESS, JAVA_BYTE, ADDRESS, ADDRESS);
    public static final MethodHandle WRITE_CUSTOM_BOARD_PARAM =
        call("LMS_WriteCustomBoardParam", ADDRESS, JAVA_BYTE, JAVA_DOUBLE, ADDRESS);
    public static final MethodHandle GET_CLOCK_FREQ = call("LMS_GetClockFreq", ADDRESS, JAVA_LONG, ADDRESS);
    public static final MethodHandle SET_CLOCK_FREQ = call("LMS_SetClockFreq", ADDRESS, JAVA_LONG, JAVA_DOUBLE);
    public static final MethodHandle VCTCXO_WRITE = call("LMS_VCTCXOWrite", ADDRESS, JAVA_SHORT);
    public static final MethodHandle VCTCXO_READ = call("LMS_VCTCXORead", ADDRESS, ADDRESS);

    // ---- GPIO ----
    public static final MethodHandle GPIO_READ = call("LMS_GPIORead", ADDRESS, ADDRESS, JAVA_LONG);
    public static final MethodHandle GPIO_WRITE = call("LMS_GPIOWrite", ADDRESS, ADDRESS, JAVA_LONG);
    public static final MethodHandle GPIO_DIR_READ = call("LMS_GPIODirRead", ADDRESS, ADDRESS, JAVA_LONG);
    public static final MethodHandle GPIO_DIR_WRITE = call("LMS_GPIODirWrite", ADDRESS, ADDRESS, JAVA_LONG);

    // ---- Streaming ----
    public static final MethodHandle SETUP_STREAM = call("LMS_SetupStream", ADDRESS, ADDRESS);
    public static final MethodHandle DESTROY_STREAM = call("LMS_DestroyStream", ADDRESS, ADDRESS);
    public static final MethodHandle START_STREAM = call("LMS_StartStream", ADDRESS);
    public static final MethodHandle STOP_STREAM = call("LMS_StopStream", ADDRESS);
    public static final MethodHandle RECV_STREAM =
        call("LMS_RecvStream", ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT);
    public static final MethodHandle SEND_STREAM =
        call("LMS_SendStream", ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_INT);
    public static final MethodHandle GET_STREAM_STATUS = call("LMS_GetStreamStatus", ADDRESS, ADDRESS);
    public static final MethodHandle UPLOAD_WFM = call("LMS_UploadWFM", ADDRESS, ADDRESS, JAVA_BYTE, JAVA_LONG, JAVA_INT);
    public static final MethodHandle ENABLE_TX_WFM = call("LMS_EnableTxWFM", ADDRESS, JAVA_INT, JAVA_BOOLEAN);

    // ---- Firmware and gateware ----
    public static final MethodHandle GET_PROGRAM_MODES = call("LMS_GetProgramModes", ADDRESS, ADDRESS);
    public static final MethodHandle PROGRAM = call("LMS_Program", ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS);

    private Native() {
    }

    private static MemorySegment symbol(String name) {
        return LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError("LimeSuiteNG has no symbol " + name));
    }

    private static MethodHandle call(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.of(JAVA_INT, arguments));
    }

    private static MethodHandle pointerCall(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.of(ADDRESS, arguments));
    }

    private static MethodHandle voidCall(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.ofVoid(arguments));
    }

    /**
     * Calls a native function returning {@code int}. This boxes its arguments,
     * which is fine for the control paths using it; the receive and transmit
     * loops call their handles directly.
     */
    public static int invoke(MethodHandle function, Object... arguments) {
        return (int) invokeObject(function, arguments);
    }

    /** Calls a native function returning a pointer. */
    public static MemorySegment invokePointer(MethodHandle function, Object... arguments) {
        return (MemorySegment) invokeObject(function, arguments);
    }

    /**
     * Calls a native function returning {@code const char*} and reads the
     * string. Returns null for a null pointer.
     */
    public static String invokeString(MethodHandle function, Object... arguments) {
        return string(invokePointer(function, arguments));
    }

    /** Calls a native function that returns nothing. */
    public static void invokeVoid(MethodHandle function, Object... arguments) {
        invokeObject(function, arguments);
    }

    /** Reads a NUL-terminated string from a pointer, or returns null for a null pointer. */
    public static String string(MemorySegment pointer) {
        if (pointer.equals(MemorySegment.NULL)) {
            return null;
        }
        return pointer.reinterpret(Integer.MAX_VALUE).getString(0);
    }

    /** Reads a NUL-terminated string from a fixed-size character array. */
    public static String string(MemorySegment array, long offset, long size) {
        MemorySegment chars = array.asSlice(offset, size);
        long length = 0;
        while (length < size && chars.get(JAVA_BYTE, length) != 0) {
            length++;
        }
        return new String(chars.asSlice(0, length).toArray(JAVA_BYTE), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Object invokeObject(MethodHandle function, Object... arguments) {
        try {
            return function.invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
    }

    /** Creates an upcall stub for {@code LMS_LogHandler}. */
    public static MemorySegment logHandler(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS), arena);
    }

    /** Creates an upcall stub for {@code lms_prog_callback_t}. */
    public static MemorySegment programCallback(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, JAVA_INT, ADDRESS), arena);
    }
}
