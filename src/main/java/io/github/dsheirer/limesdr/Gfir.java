package io.github.dsheirer.limesdr;

/** The general purpose FIR filters of the LMS7002M, {@code lms_gfir_t}. */
public enum Gfir {
    /** {@code LMS_GFIR1}, up to 40 coefficients. */
    GFIR1(40),
    /** {@code LMS_GFIR2}, up to 40 coefficients. */
    GFIR2(40),
    /** {@code LMS_GFIR3}, up to 120 coefficients. */
    GFIR3(120);

    private final int maxCoefficients;

    Gfir(int maxCoefficients) {
        this.maxCoefficients = maxCoefficients;
    }

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }

    /** Returns the most coefficients the filter takes. */
    public int maxCoefficients() {
        return maxCoefficients;
    }
}
