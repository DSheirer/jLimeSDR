package io.github.dsheirer.limesdr;

/** Test signals of the LMS7002M, {@code lms_testsig_t}. */
public enum TestSignal {
    /** {@code LMS_TESTSIG_NONE}: normal operation. */
    NONE,
    /** {@code LMS_TESTSIG_NCODIV8}: NCO at a eighth of the sample rate, half scale. */
    NCO_DIV8,
    /** {@code LMS_TESTSIG_NCODIV4}: NCO at a quarter of the sample rate, half scale. */
    NCO_DIV4,
    /** {@code LMS_TESTSIG_NCODIV8F}: NCO at a eighth of the sample rate, full scale. */
    NCO_DIV8_FULL,
    /** {@code LMS_TESTSIG_NCODIV4F}: NCO at a quarter of the sample rate, full scale. */
    NCO_DIV4_FULL,
    /** {@code LMS_TESTSIG_DC}: a DC level set by the I and Q values. */
    DC;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }

    /** Decodes a native value. */
    public static TestSignal fromCode(int code) {
        TestSignal[] values = values();
        if (code < 0 || code >= values.length) {
            throw new IllegalArgumentException("Unknown test signal: " + code);
        }
        return values[code];
    }
}
