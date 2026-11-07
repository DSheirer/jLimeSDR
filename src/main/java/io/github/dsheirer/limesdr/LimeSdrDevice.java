package io.github.dsheirer.limesdr;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import io.github.dsheirer.limesdr.internal.Layouts;
import io.github.dsheirer.limesdr.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An open LimeSDR device. Close it to release the device.
 *
 * <p>Most settings take a {@link Direction} and a channel index, from 0 to
 * {@link #channelCount} minus 1. Call {@link #init()} after opening, then
 * enable the channels, set the sample rate, frequency, antenna, gain and
 * filter, and finally {@link #calibrate}: calibration depends on the other
 * settings.
 *
 * <p>The methods are thread-safe: settings may change from any thread while
 * another thread reads a stream.
 */
public final class LimeSdrDevice implements AutoCloseable {
    /** {@code LMS_PATH_NONE}: no antenna port. */
    public static final int PATH_NONE = 0;
    /** {@code LMS_PATH_LNAH}: receive port LNA_H, high band. */
    public static final int PATH_LNAH = 1;
    /** {@code LMS_PATH_LNAL}: receive port LNA_L, low band. */
    public static final int PATH_LNAL = 2;
    /** {@code LMS_PATH_LNAW}: receive port LNA_W, wide band. */
    public static final int PATH_LNAW = 3;
    /** {@code LMS_PATH_TX1}: transmit port 1. */
    public static final int PATH_TX1 = 1;
    /** {@code LMS_PATH_TX2}: transmit port 2. */
    public static final int PATH_TX2 = 2;
    /** {@code LMS_PATH_AUTO}: let the device choose the port, if it can. */
    public static final int PATH_AUTO = 255;

    /** {@code LMS_NCO_VAL_COUNT}: frequencies or phases an NCO holds. */
    public static final int NCO_VALUE_COUNT = 16;

    /** {@code LMS_ALIGN_CH_PHASE}, combined with the channel of a receive stream. */
    static final int ALIGN_CHANNEL_PHASE = 1 << 16;

    // Set while a SampleListener runs, to refuse calls that would wait for it.
    private static final ThreadLocal<Boolean> IN_LISTENER = ThreadLocal.withInitial(() -> false);

    final Object lock = new Object();
    private final MemorySegment handle;
    private final List<LimeStream> streams = new ArrayList<>();
    private boolean closed;
    private Receiver receiver;
    private volatile Throwable receiveFailure;

    LimeSdrDevice(MemorySegment handle) {
        this.handle = handle;
    }

    // ---- Device ----

    /** Configures the LMS7002M for operation, {@code LMS_Init}. */
    public void init() throws LimeSdrException {
        call("LMS_Init", Native.INIT);
    }

    /**
     * Resets the LMS7002M to its datasheet defaults, {@code LMS_Reset}. Use
     * {@link #init()} afterwards to make it ready for operation again.
     */
    public void reset() throws LimeSdrException {
        call("LMS_Reset", Native.RESET);
    }

    /** Reads the device's name, serial number and versions. */
    public DeviceInfo deviceInfo() throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            LimeSdr.clearError();
            MemorySegment info = Native.invokePointer(Native.GET_DEVICE_INFO, handle);
            if (info.equals(MemorySegment.NULL)) {
                throw LimeSdr.failure("LMS_GetDeviceInfo", LimeSdrError.ERROR.code());
            }
            StructLayout layout = Layouts.DEVICE_INFO;
            info = info.reinterpret(layout.byteSize());
            return new DeviceInfo(
                text(info, layout, "deviceName", 32),
                text(info, layout, "expansionName", 32),
                text(info, layout, "firmwareVersion", 16),
                text(info, layout, "hardwareVersion", 16),
                text(info, layout, "protocolVersion", 16),
                info.get(JAVA_LONG, Layouts.offset(layout, "boardSerialNumber")),
                text(info, layout, "gatewareVersion", 16),
                text(info, layout, "gatewareTargetBoard", 32));
        }
    }

    private static String text(MemorySegment struct, StructLayout layout, String member, int size) {
        return Native.string(struct, Layouts.offset(layout, member), size);
    }

    /** Returns the number of receive or transmit channels. */
    public int channelCount(Direction direction) throws LimeSdrException {
        return call("LMS_GetNumChannels", Native.GET_NUM_CHANNELS, direction.isTx());
    }

    /** Enables or disables a channel. Some settings fail on a disabled channel. */
    public void enableChannel(Direction direction, int channel, boolean enabled) throws LimeSdrException {
        call("LMS_EnableChannel", Native.ENABLE_CHANNEL, direction.isTx(), channel(channel), enabled);
    }

    /** Reads the temperature of the first LMS7002M in degrees Celsius. */
    public double chipTemperature() throws LimeSdrException {
        return chipTemperature(0);
    }

    /** Reads the temperature of an LMS7002M, by chip index, in degrees Celsius. */
    public double chipTemperature(int chip) throws LimeSdrException {
        return readDouble("LMS_GetChipTemperature", Native.GET_CHIP_TEMPERATURE, (long) index(chip));
    }

    // ---- Sample rate ----

    /**
     * Sets the sample rate of every channel in both directions, in complex
     * samples per second.
     *
     * @param oversample RF oversampling ratio: 1, 2, 4, 8, 16 or 32, or 0 for the device default
     */
    public void setSampleRate(double rate, int oversample) throws LimeSdrException {
        call("LMS_SetSampleRate", Native.SET_SAMPLE_RATE, rate, oversample(oversample));
    }

    /**
     * Sets the sample rate of one direction. Receive and transmit rates are
     * tied, so this may change the other direction too; read the result with
     * {@link #sampleRate}.
     *
     * @param oversample RF oversampling ratio: 1, 2, 4, 8, 16 or 32, or 0 for the device default
     */
    public void setSampleRate(Direction direction, double rate, int oversample) throws LimeSdrException {
        call("LMS_SetSampleRateDir", Native.SET_SAMPLE_RATE_DIR, direction.isTx(), rate, oversample(oversample));
    }

    /** Reads the host and RF sample rates of a channel. */
    public SampleRate sampleRate(Direction direction, int channel) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment host = arena.allocate(JAVA_DOUBLE);
                MemorySegment rf = arena.allocate(JAVA_DOUBLE);
                LimeSdr.call("LMS_GetSampleRate", Native.GET_SAMPLE_RATE, handle, direction.isTx(), channel(channel),
                    host, rf);
                return new SampleRate(host.get(JAVA_DOUBLE, 0), rf.get(JAVA_DOUBLE, 0));
            }
        }
    }

    /** Returns the supported sample rates. */
    public Range sampleRateRange(Direction direction) throws LimeSdrException {
        return readRange("LMS_GetSampleRateRange", Native.GET_SAMPLE_RATE_RANGE, direction.isTx());
    }

    // ---- Tuning ----

    /**
     * Sets the RF center frequency in Hz. The two channels of an LMS7002M share
     * an LO, so changing one channel often changes the other.
     */
    public void setFrequency(Direction direction, int channel, double hertz) throws LimeSdrException {
        call("LMS_SetLOFrequency", Native.SET_LO_FREQUENCY, direction.isTx(), channel(channel), hertz);
    }

    /** Reads the RF center frequency in Hz. */
    public double frequency(Direction direction, int channel) throws LimeSdrException {
        return readDouble("LMS_GetLOFrequency", Native.GET_LO_FREQUENCY, direction.isTx(), channel(channel));
    }

    /** Returns the supported RF center frequencies in Hz. */
    public Range frequencyRange(Direction direction) throws LimeSdrException {
        return readRange("LMS_GetLOFrequencyRange", Native.GET_LO_FREQUENCY_RANGE, direction.isTx());
    }

    // ---- Antennas ----

    /** Lists the antenna ports of a channel by name; a port's index is its position in the list. */
    public List<String> antennas(Direction direction, int channel) throws LimeSdrException {
        return names("LMS_GetAntennaList", Native.GET_ANTENNA_LIST, direction.isTx(), channel(channel));
    }

    /** Selects an antenna port by index, such as {@link #PATH_LNAW}. */
    public void setAntenna(Direction direction, int channel, int index) throws LimeSdrException {
        call("LMS_SetAntenna", Native.SET_ANTENNA, direction.isTx(), channel(channel), (long) index(index));
    }

    /** Returns the index of the selected antenna port. */
    public int antenna(Direction direction, int channel) throws LimeSdrException {
        return call("LMS_GetAntenna", Native.GET_ANTENNA, direction.isTx(), channel(channel));
    }

    /** Returns the recommended frequency range of an antenna port in Hz. */
    public Range antennaBandwidth(Direction direction, int channel, int index) throws LimeSdrException {
        return readRange("LMS_GetAntennaBW", Native.GET_ANTENNA_BW, direction.isTx(), channel(channel),
            (long) index(index));
    }

    // ---- Gain ----

    /**
     * Sets the combined gain in dB, from 0 to 73; the library distributes it
     * over the amplifiers. To set one receive amplifier, use
     * {@link #writeParameter} with {@link Lms7Parameter#G_LNA_RFE},
     * {@link Lms7Parameter#G_TIA_RFE} or {@link Lms7Parameter#G_PGA_RBB}.
     */
    public void setGain(Direction direction, int channel, int decibels) throws LimeSdrException {
        if (decibels < 0) {
            throw new IllegalArgumentException("Gain must not be negative: " + decibels);
        }
        call("LMS_SetGaindB", Native.SET_GAIN_DB, direction.isTx(), channel(channel), decibels);
    }

    /**
     * Reads the combined gain in dB, as LimeSuiteNG reports it. It currently
     * reports 12 dB more than {@link #setGain} set: {@code LMS_GetGaindB} still
     * adds the offset of the original LimeSuite API, which {@code LMS_SetGaindB}
     * no longer subtracts. The value is passed through unchanged.
     */
    public int gain(Direction direction, int channel) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment gain = arena.allocate(JAVA_INT);
                LimeSdr.call("LMS_GetGaindB", Native.GET_GAIN_DB, handle, direction.isTx(), channel(channel), gain);
                return gain.get(JAVA_INT, 0);
            }
        }
    }

    /** Sets the combined gain from 0 to 1, where 1 is the maximum. */
    public void setNormalizedGain(Direction direction, int channel, double gain) throws LimeSdrException {
        if (!(gain >= 0 && gain <= 1)) {
            throw new IllegalArgumentException("Normalized gain must be from 0 to 1: " + gain);
        }
        call("LMS_SetNormalizedGain", Native.SET_NORMALIZED_GAIN, direction.isTx(), channel(channel), gain);
    }

    /** Reads the combined gain from 0 to 1. */
    public double normalizedGain(Direction direction, int channel) throws LimeSdrException {
        return readDouble("LMS_GetNormalizedGain", Native.GET_NORMALIZED_GAIN, direction.isTx(), channel(channel));
    }

    // ---- Filters ----

    /** Sets the analog low-pass filter for an RF bandwidth in Hz, and enables it. */
    public void setLpfBandwidth(Direction direction, int channel, double hertz) throws LimeSdrException {
        call("LMS_SetLPFBW", Native.SET_LPF_BW, direction.isTx(), channel(channel), hertz);
    }

    /** Returns the analog low-pass filter bandwidth last set, in Hz; the device cannot report it. */
    public double lpfBandwidth(Direction direction, int channel) throws LimeSdrException {
        return readDouble("LMS_GetLPFBW", Native.GET_LPF_BW, direction.isTx(), channel(channel));
    }

    /** Returns the supported analog low-pass filter bandwidths in Hz. */
    public Range lpfBandwidthRange(Direction direction) throws LimeSdrException {
        return readRange("LMS_GetLPFBWRange", Native.GET_LPF_BW_RANGE, direction.isTx());
    }

    /** Enables or bypasses the analog low-pass filter without changing its bandwidth. */
    public void setLpfEnabled(Direction direction, int channel, boolean enabled) throws LimeSdrException {
        call("LMS_SetLPF", Native.SET_LPF, direction.isTx(), channel(channel), enabled);
    }

    /**
     * Configures the digital filters as a low-pass filter of an RF bandwidth in
     * Hz, or disables them. Set the sample rate first.
     */
    public void setGfirLpf(Direction direction, int channel, boolean enabled, double hertz) throws LimeSdrException {
        call("LMS_SetGFIRLPF", Native.SET_GFIR_LPF, direction.isTx(), channel(channel), enabled, hertz);
    }

    /** Loads coefficients from -1 to 1 into a digital filter. */
    public void setGfirCoefficients(Direction direction, int channel, Gfir filter, double[] coefficients)
            throws LimeSdrException {
        if (coefficients.length > filter.maxCoefficients()) {
            throw new IllegalArgumentException(filter + " takes at most " + filter.maxCoefficients() + " coefficients");
        }
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment values = arena.allocate(JAVA_DOUBLE, Math.max(1, coefficients.length));
                MemorySegment.copy(coefficients, 0, values, JAVA_DOUBLE, 0, coefficients.length);
                LimeSdr.call("LMS_SetGFIRCoeff", Native.SET_GFIR_COEFF, handle, direction.isTx(), channel(channel),
                    filter.code(), values, (long) coefficients.length);
            }
        }
    }

    /** Reads a digital filter's coefficients: {@link Gfir#maxCoefficients()} values. */
    public double[] gfirCoefficients(Direction direction, int channel, Gfir filter) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment values = arena.allocate(JAVA_DOUBLE, filter.maxCoefficients());
                LimeSdr.call("LMS_GetGFIRCoeff", Native.GET_GFIR_COEFF, handle, direction.isTx(), channel(channel),
                    filter.code(), values);
                return values.toArray(JAVA_DOUBLE);
            }
        }
    }

    /** Enables or disables a digital filter. */
    public void setGfirEnabled(Direction direction, int channel, Gfir filter, boolean enabled)
            throws LimeSdrException {
        call("LMS_SetGFIR", Native.SET_GFIR, direction.isTx(), channel(channel), filter.code(), enabled);
    }

    // ---- Calibration and test signals ----

    /** Calibrates a channel for a bandwidth in Hz. Configure the channel first. */
    public void calibrate(Direction direction, int channel, double bandwidth) throws LimeSdrException {
        calibrate(direction, channel, bandwidth, 0);
    }

    /** Calibrates a channel with additional flags, which are normally 0. */
    public void calibrate(Direction direction, int channel, double bandwidth, int flags) throws LimeSdrException {
        call("LMS_Calibrate", Native.CALIBRATE, direction.isTx(), channel(channel), bandwidth, flags);
    }

    /** Applies a test signal, or returns to normal operation with {@link TestSignal#NONE}. */
    public void setTestSignal(Direction direction, int channel, TestSignal signal) throws LimeSdrException {
        setTestSignal(direction, channel, signal, (short) 0, (short) 0);
    }

    /** Applies a test signal; the I and Q values set the level of {@link TestSignal#DC}. */
    public void setTestSignal(Direction direction, int channel, TestSignal signal, short dcI, short dcQ)
            throws LimeSdrException {
        call("LMS_SetTestSignal", Native.SET_TEST_SIGNAL, direction.isTx(), channel(channel), signal.code(), dcI, dcQ);
    }

    /** Reads the active test signal. */
    public TestSignal testSignal(Direction direction, int channel) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment signal = arena.allocate(JAVA_INT);
                LimeSdr.call("LMS_GetTestSignal", Native.GET_TEST_SIGNAL, handle, direction.isTx(), channel(channel),
                    signal);
                return TestSignal.fromCode(signal.get(JAVA_INT, 0));
            }
        }
    }

    /**
     * Loads an LMS7002M configuration file. It configures only the chip: set the
     * sample rate afterwards to configure the FPGA for streaming.
     */
    public void loadConfig(Path file) throws LimeSdrException {
        pathCall("LMS_LoadConfig", Native.LOAD_CONFIG, file);
    }

    /** Saves the LMS7002M configuration to a file. */
    public void saveConfig(Path file) throws LimeSdrException {
        pathCall("LMS_SaveConfig", Native.SAVE_CONFIG, file);
    }

    // ---- NCO ----

    /**
     * Puts the NCO in frequency mode with {@value #NCO_VALUE_COUNT} frequencies
     * in Hz, not negative, and starts it at index 0. Fewer values are padded
     * with zeros.
     *
     * @param phaseOffset phase offset in degrees
     */
    public void setNcoFrequencies(Direction direction, int channel, double[] frequencies, double phaseOffset)
            throws LimeSdrException {
        ncoCall("LMS_SetNCOFrequency", Native.SET_NCO_FREQUENCY, direction, channel, frequencies, phaseOffset);
    }

    /** Reads the frequencies in Hz and the phase offset in degrees of frequency mode. */
    public NcoConfiguration ncoFrequencies(Direction direction, int channel) throws LimeSdrException {
        return ncoRead("LMS_GetNCOFrequency", Native.GET_NCO_FREQUENCY, direction, channel);
    }

    /**
     * Puts the NCO in phase mode with {@value #NCO_VALUE_COUNT} phase offsets in
     * degrees, not negative, and starts it at index 0. Fewer values are padded
     * with zeros.
     *
     * @param frequency NCO frequency in Hz
     */
    public void setNcoPhases(Direction direction, int channel, double[] phases, double frequency)
            throws LimeSdrException {
        ncoCall("LMS_SetNCOPhase", Native.SET_NCO_PHASE, direction, channel, phases, frequency);
    }

    /** Reads the phase offsets in degrees and the frequency in Hz of phase mode. */
    public NcoConfiguration ncoPhases(Direction direction, int channel) throws LimeSdrException {
        return ncoRead("LMS_GetNCOPhase", Native.GET_NCO_PHASE, direction, channel);
    }

    /**
     * Selects one of the NCO's values, or disables the NCO with -1.
     *
     * @param downconvert true to mix down, false to mix up
     */
    public void setNcoIndex(Direction direction, int channel, int index, boolean downconvert) throws LimeSdrException {
        if (index < -1 || index >= NCO_VALUE_COUNT) {
            throw new IllegalArgumentException("NCO index must be from -1 to " + (NCO_VALUE_COUNT - 1) + ": " + index);
        }
        call("LMS_SetNCOIndex", Native.SET_NCO_INDEX, direction.isTx(), channel(channel), index, downconvert);
    }

    /** Returns the selected NCO value's index. */
    public int ncoIndex(Direction direction, int channel) throws LimeSdrException {
        return call("LMS_GetNCOIndex", Native.GET_NCO_INDEX, direction.isTx(), channel(channel));
    }

    private void ncoCall(String function, MethodHandle native_, Direction direction, int channel, double[] values,
            double shared) throws LimeSdrException {
        if (values.length > NCO_VALUE_COUNT) {
            throw new IllegalArgumentException("At most " + NCO_VALUE_COUNT + " values: " + values.length);
        }
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment array = arena.allocate(JAVA_DOUBLE, NCO_VALUE_COUNT);
                MemorySegment.copy(values, 0, array, JAVA_DOUBLE, 0, values.length);
                LimeSdr.call(function, native_, handle, direction.isTx(), channel(channel), array, shared);
            }
        }
    }

    private NcoConfiguration ncoRead(String function, MethodHandle native_, Direction direction, int channel)
            throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment array = arena.allocate(JAVA_DOUBLE, NCO_VALUE_COUNT);
                MemorySegment shared = arena.allocate(JAVA_DOUBLE);
                LimeSdr.call(function, native_, handle, direction.isTx(), channel(channel), array, shared);
                return new NcoConfiguration(array.toArray(JAVA_DOUBLE), shared.get(JAVA_DOUBLE, 0));
            }
        }
    }

    // ---- Registers, parameters and clocks ----

    /** Enables or disables caching of LMS7002M and FPGA register values. */
    public void setCacheEnabled(boolean enabled) throws LimeSdrException {
        call("LMS_EnableCache", Native.ENABLE_CACHE, enabled);
    }

    /**
     * Synchronizes the register cache with the chip.
     *
     * @param toChip true to write the cache to the chip, false to read the chip into the cache
     */
    public void synchronize(boolean toChip) throws LimeSdrException {
        call("LMS_Synchronize", Native.SYNCHRONIZE, toChip);
    }

    /** Reads an LMS7002M register. */
    public int readLmsRegister(int address) throws LimeSdrException {
        return readShort("LMS_ReadLMSReg", Native.READ_LMS_REG, address);
    }

    /** Writes an LMS7002M register. */
    public void writeLmsRegister(int address, int value) throws LimeSdrException {
        call("LMS_WriteLMSReg", Native.WRITE_LMS_REG, address, u16(value));
    }

    /** Reads an FPGA register. */
    public int readFpgaRegister(int address) throws LimeSdrException {
        return readShort("LMS_ReadFPGAReg", Native.READ_FPGA_REG, address);
    }

    /** Writes an FPGA register. */
    public void writeFpgaRegister(int address, int value) throws LimeSdrException {
        call("LMS_WriteFPGAReg", Native.WRITE_FPGA_REG, address, u16(value));
    }

    /** Reads an LMS7002M parameter, a bit field of a register, for the channel {@link Lms7Parameter#MAC} selects. */
    public int readParameter(Lms7Parameter parameter) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment value = arena.allocate(JAVA_SHORT);
                LimeSdr.call("LMS_ReadParam", Native.READ_PARAM, handle, parameterStruct(parameter, arena), value);
                return Short.toUnsignedInt(value.get(JAVA_SHORT, 0));
            }
        }
    }

    /** Writes an LMS7002M parameter, a bit field of a register, for the channel {@link Lms7Parameter#MAC} selects. */
    public void writeParameter(Lms7Parameter parameter, int value) throws LimeSdrException {
        if (value < 0 || value > parameter.maxValue()) {
            throw new IllegalArgumentException(parameter.name() + " must be from 0 to " + parameter.maxValue()
                + ": " + value);
        }
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                LimeSdr.call("LMS_WriteParam", Native.WRITE_PARAM, handle, parameterStruct(parameter, arena),
                    (short) value);
            }
        }
    }

    private static MemorySegment parameterStruct(Lms7Parameter parameter, Arena arena) {
        StructLayout layout = Layouts.LMS7_PARAMETER;
        MemorySegment struct = arena.allocate(layout);
        struct.set(JAVA_SHORT, Layouts.offset(layout, "address"), (short) parameter.address());
        struct.set(JAVA_BYTE, Layouts.offset(layout, "msb"), (byte) parameter.msb());
        struct.set(JAVA_BYTE, Layouts.offset(layout, "lsb"), (byte) parameter.lsb());
        struct.set(JAVA_SHORT, Layouts.offset(layout, "defaultValue"), (short) parameter.defaultValue());
        struct.set(ADDRESS, Layouts.offset(layout, "name"), arena.allocateFrom(parameter.name()));
        struct.set(ADDRESS, Layouts.offset(layout, "tooltip"), arena.allocateFrom(""));
        return struct;
    }

    /** Reads a custom board parameter, such as {@link BoardParameter#TEMPERATURE}. */
    public BoardParameter readBoardParameter(int id) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment value = arena.allocate(JAVA_DOUBLE);
                MemorySegment units = arena.allocate(Layouts.NAME_SIZE);
                LimeSdr.call("LMS_ReadCustomBoardParam", Native.READ_CUSTOM_BOARD_PARAM, handle, u8(id), value, units);
                return new BoardParameter(value.get(JAVA_DOUBLE, 0), Native.string(units, 0, Layouts.NAME_SIZE));
            }
        }
    }

    /** Writes a custom board parameter, such as {@link BoardParameter#DAC}; units may be null. */
    public void writeBoardParameter(int id, double value, String units) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment name = units == null ? MemorySegment.NULL : name(units, arena);
                LimeSdr.call("LMS_WriteCustomBoardParam", Native.WRITE_CUSTOM_BOARD_PARAM, handle, u8(id), value,
                    name);
            }
        }
    }

    /** Reads a clock frequency in Hz. */
    public double clockFrequency(Clock clock) throws LimeSdrException {
        return readDouble("LMS_GetClockFreq", Native.GET_CLOCK_FREQ, (long) clock.id());
    }

    /**
     * Sets a clock frequency in Hz. Zero or a negative frequency only retunes,
     * where supported; for {@link Clock#EXTREF} it selects the internal reference.
     */
    public void setClockFrequency(Clock clock, double hertz) throws LimeSdrException {
        call("LMS_SetClockFreq", Native.SET_CLOCK_FREQ, (long) clock.id(), hertz);
    }

    /** Reads the VCTCXO trim DAC value stored in non-volatile memory, loaded at power-on. */
    public int readVctcxoTrim() throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment value = arena.allocate(JAVA_SHORT);
                LimeSdr.call("LMS_VCTCXORead", Native.VCTCXO_READ, handle, value);
                return Short.toUnsignedInt(value.get(JAVA_SHORT, 0));
            }
        }
    }

    /**
     * Writes the VCTCXO trim DAC value to non-volatile memory and selects the
     * VCTCXO as the clock source. Use {@link #writeBoardParameter} with
     * {@link BoardParameter#DAC} to change it until the next power cycle only.
     */
    public void writeVctcxoTrim(int value) throws LimeSdrException {
        call("LMS_VCTCXOWrite", Native.VCTCXO_WRITE, u16(value));
    }

    // ---- GPIO ----

    /** Reads GPIO levels, 8 per byte, least significant bit first. */
    public byte[] readGpio(int bytes) throws LimeSdrException {
        return readBytes("LMS_GPIORead", Native.GPIO_READ, bytes);
    }

    /** Writes GPIO levels, 8 per byte, least significant bit first. */
    public void writeGpio(byte[] values) throws LimeSdrException {
        writeBytes("LMS_GPIOWrite", Native.GPIO_WRITE, values);
    }

    /** Reads GPIO directions, 8 per byte, least significant bit first: 0 input, 1 output. */
    public byte[] readGpioDirection(int bytes) throws LimeSdrException {
        return readBytes("LMS_GPIODirRead", Native.GPIO_DIR_READ, bytes);
    }

    /** Writes GPIO directions, 8 per byte, least significant bit first: 0 input, 1 output. */
    public void writeGpioDirection(byte[] values) throws LimeSdrException {
        writeBytes("LMS_GPIODirWrite", Native.GPIO_DIR_WRITE, values);
    }

    // ---- Streaming ----

    /**
     * Opens a receive stream. Open every stream before starting any; see
     * {@link LimeStream}.
     *
     * @throws IllegalStateException if a stream of this device is running
     */
    public RxStream openRxStream(StreamConfig config) throws LimeSdrException {
        return openStream(new RxStream(this, config));
    }

    /**
     * Opens a transmit stream. Open every stream before starting any; see
     * {@link LimeStream}.
     *
     * @throws IllegalStateException if a stream of this device is running
     */
    public TxStream openTxStream(StreamConfig config) throws LimeSdrException {
        if (config.alignPhase()) {
            throw new IllegalArgumentException("Phase alignment applies to receive streams only");
        }
        return openStream(new TxStream(this, config));
    }

    private <T extends LimeStream> T openStream(T stream) throws LimeSdrException {
        synchronized (lock) {
            try {
                ensureOpen();
                for (LimeStream open : streams) {
                    // Setting up a stream recreates the device's shared stream,
                    // which would pull it from under one that is running.
                    if (open.isStarted()) {
                        throw new IllegalStateException("Stop the device's streams before opening another");
                    }
                    if (open.direction() == stream.direction() && open.channel() == stream.channel()) {
                        throw new IllegalStateException(stream.direction() + " channel " + stream.channel()
                            + " already has a stream");
                    }
                }
                stream.setup();
            } catch (LimeSdrException | RuntimeException e) {
                stream.discard();
                throw e;
            }
            streams.add(stream);
            return stream;
        }
    }

    void streamClosed(LimeStream stream) {
        streams.remove(stream);
    }

    /**
     * Opens and starts a receive stream and delivers its samples to a listener
     * on a thread of its own, in buffers of {@code samplesPerBuffer} I/Q pairs.
     * Configure the channel first. {@link #stopRx()} stops it.
     *
     * @throws IllegalStateException if already receiving
     */
    public void startRx(StreamConfig config, int samplesPerBuffer, SampleListener listener) throws LimeSdrException {
        Objects.requireNonNull(listener, "listener");
        if (samplesPerBuffer <= 0) {
            throw new IllegalArgumentException("Invalid buffer size: " + samplesPerBuffer);
        }
        rejectListener("startRx");
        Receiver finished;
        synchronized (lock) {
            finished = receiver != null && !receiver.running ? receiver : null;
        }
        if (finished != null) {
            // Its listener ended it; wait for it to release its stream.
            finished.shutdown();
        }
        synchronized (lock) {
            ensureOpen();
            if (receiver != null) {
                if (receiver.isAlive() && receiver.running) {
                    throw new IllegalStateException("Already receiving");
                }
                receiver = null;
            }
            RxStream stream = openRxStream(config);
            try {
                stream.start();
            } catch (LimeSdrException | RuntimeException e) {
                stream.close();
                throw e;
            }
            receiveFailure = null;
            receiver = new Receiver(stream, samplesPerBuffer, listener);
            receiver.start();
        }
    }

    /** Stops {@link #startRx} receiving and closes its stream. Does nothing if not receiving. */
    public void stopRx() throws LimeSdrException {
        rejectListener("stopRx");
        Receiver stopping;
        synchronized (lock) {
            stopping = receiver;
            receiver = null;
        }
        if (stopping != null) {
            // Not holding the lock: the listener may be waiting for it.
            stopping.shutdown();
        }
    }

    /** Returns true while {@link #startRx} delivers samples. */
    public boolean isReceiving() {
        synchronized (lock) {
            return receiver != null && receiver.isAlive() && receiver.running;
        }
    }

    /**
     * Returns the exception that stopped {@link #startRx} receiving, thrown by
     * the listener or the library, or null.
     */
    public Throwable receiveFailure() {
        return receiveFailure;
    }

    // ---- Waveform playback ----

    /**
     * Uploads a waveform of 16-bit I/Q samples to the board's memory for
     * playback with {@link #enableTxWaveform}: one array per channel, one or
     * two channels of equal length, the number of I/Q pairs a multiple of 4.
     */
    public void uploadWaveform(WaveformFormat format, short[]... channels) throws LimeSdrException {
        if (format == WaveformFormat.F32) {
            throw new IllegalArgumentException("Use the float[] method for F32 waveforms");
        }
        int length = waveformLength(channels.length, channels.length == 0 ? 0 : channels[0].length,
            channel -> channels[channel].length);
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pointers = arena.allocate(ADDRESS, channels.length);
                for (int i = 0; i < channels.length; i++) {
                    pointers.setAtIndex(ADDRESS, i, arena.allocateFrom(JAVA_SHORT, channels[i]));
                }
                LimeSdr.call("LMS_UploadWFM", Native.UPLOAD_WFM, handle, pointers, (byte) channels.length,
                    (long) length / 2, format.code());
            }
        }
    }

    /**
     * Uploads a waveform of float I/Q samples from -1 to 1 to the board's memory;
     * see {@link #uploadWaveform(WaveformFormat, short[]...)}.
     */
    public void uploadWaveform(float[]... channels) throws LimeSdrException {
        int length = waveformLength(channels.length, channels.length == 0 ? 0 : channels[0].length,
            channel -> channels[channel].length);
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pointers = arena.allocate(ADDRESS, channels.length);
                for (int i = 0; i < channels.length; i++) {
                    pointers.setAtIndex(ADDRESS, i, arena.allocateFrom(JAVA_FLOAT, channels[i]));
                }
                LimeSdr.call("LMS_UploadWFM", Native.UPLOAD_WFM, handle, pointers, (byte) channels.length,
                    (long) length / 2, WaveformFormat.F32.code());
            }
        }
    }

    private static int waveformLength(int count, int length, java.util.function.IntUnaryOperator lengths) {
        if (count < 1 || count > 2) {
            throw new IllegalArgumentException("A waveform has 1 or 2 channels, not " + count);
        }
        for (int i = 0; i < count; i++) {
            if (lengths.applyAsInt(i) != length) {
                throw new IllegalArgumentException("Waveform channels differ in length");
            }
        }
        if (length == 0 || length % 8 != 0) {
            throw new IllegalArgumentException("Waveform I/Q pairs must be a positive multiple of 4");
        }
        return length;
    }

    /** Starts or stops transmitting the uploaded waveform on a channel. */
    public void enableTxWaveform(int channel, boolean active) throws LimeSdrException {
        call("LMS_EnableTxWFM", Native.ENABLE_TX_WFM, index(channel), active);
    }

    // ---- Firmware and gateware ----

    /** Lists the memories that {@link #program} can write, such as firmware or FPGA flash. */
    public List<String> programModes() throws LimeSdrException {
        return names("LMS_GetProgramModes", Native.GET_PROGRAM_MODES);
    }

    /**
     * Writes a firmware or gateware image to one of the {@link #programModes()}.
     * Writing the wrong image can make the device unusable until it is
     * recovered.
     *
     * @param listener receives progress and can abort; may be null
     */
    public void program(String mode, byte[] image, ProgressListener listener) throws LimeSdrException {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(image, "image");
        synchronized (lock) {
            ensureOpen();
            // Shared: the library may report progress from another thread.
            try (Arena arena = Arena.ofShared()) {
                MemorySegment data = arena.allocate(Math.max(1, image.length));
                MemorySegment.copy(image, 0, data, JAVA_BYTE, 0, image.length);
                MemorySegment callback = MemorySegment.NULL;
                if (listener != null) {
                    callback = Native.programCallback(ProgressBridge.handle(listener), arena);
                }
                LimeSdr.call("LMS_Program", Native.PROGRAM, handle, data, (long) image.length, name(mode, arena),
                    callback);
            }
        }
    }

    /** Adapts a ProgressListener to {@code lms_prog_callback_t}, which returns true to abort. */
    private static final class ProgressBridge {
        private static final MethodHandle INVOKE;

        static {
            try {
                INVOKE = MethodHandles.lookup().findStatic(ProgressBridge.class, "invoke",
                    MethodType.methodType(boolean.class, ProgressListener.class, int.class, int.class,
                        MemorySegment.class));
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        static MethodHandle handle(ProgressListener listener) {
            return INVOKE.bindTo(listener);
        }

        @SuppressWarnings("unused")
        private static boolean invoke(ProgressListener listener, int sent, int total, MemorySegment message) {
            try {
                return !listener.onProgress(sent, total, Native.string(message));
            } catch (Throwable t) {
                return true;
            }
        }
    }

    // ---- Lifetime ----

    /**
     * Stops receiving, closes the device's streams and releases the device.
     * Further calls on it throw {@link IllegalStateException}.
     */
    @Override
    public void close() throws LimeSdrException {
        rejectListener("close");
        stopRx();
        synchronized (lock) {
            if (closed) {
                return;
            }
            LimeSdrException failure = null;
            for (LimeStream stream : List.copyOf(streams)) {
                try {
                    stream.close();
                } catch (LimeSdrException e) {
                    failure = failure == null ? e : failure;
                }
            }
            closed = true;
            synchronized (LimeSdr.LOCK) {
                LimeSdr.call("LMS_Close", Native.CLOSE, handle);
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** Returns true once the device is closed. */
    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    MemorySegment handle() {
        ensureOpen();
        return handle;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Device is closed");
        }
    }

    private static void rejectListener(String method) {
        if (IN_LISTENER.get()) {
            throw new IllegalStateException(method + " cannot be called from a SampleListener");
        }
    }

    // ---- Call helpers ----

    /** Calls a function whose first argument is the device handle. */
    private int call(String function, MethodHandle native_, Object... arguments) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            return LimeSdr.call(function, native_, withHandle(arguments));
        }
    }

    private Object[] withHandle(Object... arguments) {
        Object[] all = new Object[arguments.length + 1];
        all[0] = handle;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        return all;
    }

    /** Calls a function whose last argument is a {@code float_type*} result. */
    private double readDouble(String function, MethodHandle native_, Object... arguments) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = arena.allocate(JAVA_DOUBLE);
                LimeSdr.call(function, native_, withResult(result, arguments));
                return result.get(JAVA_DOUBLE, 0);
            }
        }
    }

    /** Calls a function whose last argument is a {@code uint16_t*} result. */
    private int readShort(String function, MethodHandle native_, Object... arguments) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = arena.allocate(JAVA_SHORT);
                LimeSdr.call(function, native_, withResult(result, arguments));
                return Short.toUnsignedInt(result.get(JAVA_SHORT, 0));
            }
        }
    }

    /** Calls a function whose last argument is an {@code lms_range_t*} result. */
    private Range readRange(String function, MethodHandle native_, Object... arguments) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                StructLayout layout = Layouts.RANGE;
                MemorySegment result = arena.allocate(layout);
                LimeSdr.call(function, native_, withResult(result, arguments));
                return new Range(result.get(JAVA_DOUBLE, Layouts.offset(layout, "min")),
                    result.get(JAVA_DOUBLE, Layouts.offset(layout, "max")),
                    result.get(JAVA_DOUBLE, Layouts.offset(layout, "step")));
            }
        }
    }

    /**
     * Calls a function that fills an {@code lms_name_t} list, or returns its
     * length for a null list, and reads the names.
     */
    private List<String> names(String function, MethodHandle native_, Object... arguments) throws LimeSdrException {
        synchronized (lock) {
            ensureOpen();
            int count = LimeSdr.call(function, native_, withResult(MemorySegment.NULL, arguments));
            if (count == 0) {
                return List.of();
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment list = arena.allocate(Layouts.NAME_SIZE * count);
                int filled = LimeSdr.call(function, native_, withResult(list, arguments));
                List<String> names = new ArrayList<>(filled);
                for (int i = 0; i < Math.min(count, filled); i++) {
                    names.add(Native.string(list, i * Layouts.NAME_SIZE, Layouts.NAME_SIZE));
                }
                return List.copyOf(names);
            }
        }
    }

    private Object[] withResult(MemorySegment result, Object... arguments) {
        Object[] all = new Object[arguments.length + 2];
        all[0] = handle;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        all[all.length - 1] = result;
        return all;
    }

    private byte[] readBytes(String function, MethodHandle native_, int bytes) throws LimeSdrException {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Invalid length: " + bytes);
        }
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(bytes);
                LimeSdr.call(function, native_, handle, buffer, (long) bytes);
                return buffer.toArray(JAVA_BYTE);
            }
        }
    }

    private void writeBytes(String function, MethodHandle native_, byte[] values) throws LimeSdrException {
        if (values.length == 0) {
            throw new IllegalArgumentException("No values");
        }
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                LimeSdr.call(function, native_, handle, arena.allocateFrom(JAVA_BYTE, values), (long) values.length);
            }
        }
    }

    private void pathCall(String function, MethodHandle native_, Path file) throws LimeSdrException {
        Objects.requireNonNull(file, "file");
        synchronized (lock) {
            ensureOpen();
            try (Arena arena = Arena.ofConfined()) {
                LimeSdr.call(function, native_, handle, arena.allocateFrom(file.toString()));
            }
        }
    }

    /** Copies a string into a zero-padded {@code lms_name_t}. */
    private static MemorySegment name(String value, Arena arena) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= Layouts.NAME_SIZE) {
            throw new IllegalArgumentException("Name longer than " + (Layouts.NAME_SIZE - 1) + " bytes: " + value);
        }
        MemorySegment name = arena.allocate(Layouts.NAME_SIZE);
        MemorySegment.copy(bytes, 0, name, JAVA_BYTE, 0, bytes.length);
        return name;
    }

    private static long channel(int channel) {
        if (channel < 0) {
            throw new IllegalArgumentException("Invalid channel: " + channel);
        }
        return channel;
    }

    private static int index(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        return index;
    }

    private static long oversample(int oversample) {
        return switch (oversample) {
            case 0, 1, 2, 4, 8, 16, 32 -> oversample;
            default -> throw new IllegalArgumentException("Oversampling must be 0, 1, 2, 4, 8, 16 or 32: " + oversample);
        };
    }

    private static short u16(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of 16-bit range: " + value);
        }
        return (short) value;
    }

    private static byte u8(int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value out of 8-bit range: " + value);
        }
        return (byte) value;
    }

    // ---- Receiver ----

    /** Reads a stream on its own thread and hands the samples to a listener. */
    private final class Receiver extends Thread {
        private static final int READ_TIMEOUT_MILLIS = 250;

        private final RxStream stream;
        private final int samplesPerBuffer;
        private final SampleListener listener;
        volatile boolean running = true;

        Receiver(RxStream stream, int samplesPerBuffer, SampleListener listener) {
            super("jlimesdr-rx-" + stream.channel());
            setDaemon(true);
            this.stream = stream;
            this.samplesPerBuffer = samplesPerBuffer;
            this.listener = listener;
        }

        @Override
        public void run() {
            SampleFormat format = stream.config().format();
            long bytes = (long) samplesPerBuffer * format.bytesPerSample();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment buffer = arena.allocate(bytes, 8);
                while (running) {
                    int read;
                    synchronized (stream.io) {
                        if (stream.isClosed()) {
                            break;
                        }
                        read = stream.receive(buffer, samplesPerBuffer, READ_TIMEOUT_MILLIS);
                    }
                    if (read == 0) {
                        continue;
                    }
                    // A view that dies with this arena, so the listener cannot
                    // keep using memory that the next read overwrites.
                    try (Arena view = Arena.ofConfined()) {
                        MemorySegment samples = buffer.asSlice(0, (long) read * format.bytesPerSample())
                            .reinterpret(view, null);
                        SampleBuffer block = new SampleBuffer(stream.channel(), format, samples,
                            stream.lastTimestamp());
                        IN_LISTENER.set(true);
                        try {
                            if (!listener.onSamples(block)) {
                                running = false;
                            }
                        } finally {
                            IN_LISTENER.set(false);
                        }
                    }
                }
            } catch (Throwable t) {
                receiveFailure = t;
            } finally {
                running = false;
                closeStream();
            }
        }

        void shutdown() throws LimeSdrException {
            running = false;
            if (Thread.currentThread() != this) {
                boolean interrupted = false;
                while (isAlive()) {
                    try {
                        join();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            stream.close();
        }

        private void closeStream() {
            try {
                stream.close();
            } catch (LimeSdrException | RuntimeException e) {
                if (receiveFailure == null) {
                    receiveFailure = e;
                }
            }
        }
    }
}
