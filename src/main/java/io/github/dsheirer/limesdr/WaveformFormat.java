package io.github.dsheirer.limesdr;

/** Sample format of a waveform for {@link LimeSdrDevice#uploadWaveform}. */
public enum WaveformFormat {
    /** 16-bit integers from -2048 to 2047. */
    I12(0),
    /** 16-bit integers from -32768 to 32767. */
    I16(1),
    /** Floats from -1 to 1. */
    F32(2);

    private final int code;

    WaveformFormat(int code) {
        this.code = code;
    }

    /** Returns the native value. */
    public int code() {
        return code;
    }
}
