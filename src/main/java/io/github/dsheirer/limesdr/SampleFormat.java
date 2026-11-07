package io.github.dsheirer.limesdr;

/** Format of the samples exchanged with the host, {@code lms_stream_t.dataFmt}. */
public enum SampleFormat {
    /** {@code LMS_FMT_F32}: 32-bit floats from -1 to 1, I then Q. */
    F32(0, 8),
    /** {@code LMS_FMT_I16}: 16-bit integers using the full range, I then Q. */
    I16(1, 4),
    /** {@code LMS_FMT_I12}: 12-bit integers, -2048 to 2047, in 16-bit values, I then Q. */
    I12(2, 4);

    private final int code;
    private final int bytesPerSample;

    SampleFormat(int code, int bytesPerSample) {
        this.code = code;
        this.bytesPerSample = bytesPerSample;
    }

    /** Returns the native value. */
    public int code() {
        return code;
    }

    /** Returns the size of one complex sample, an I/Q pair, in bytes. */
    public int bytesPerSample() {
        return bytesPerSample;
    }

    /** Returns true for the formats held in 16-bit integers. */
    public boolean isInteger() {
        return this != F32;
    }

    /** Returns the value that a full-scale sample has, to scale samples to -1 to 1. */
    public float fullScale() {
        return switch (this) {
            case F32 -> 1f;
            case I16 -> 32768f;
            case I12 -> 2048f;
        };
    }
}
