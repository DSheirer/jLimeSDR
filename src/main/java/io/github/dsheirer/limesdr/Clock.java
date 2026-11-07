package io.github.dsheirer.limesdr;

/** Clocks of a device, the {@code LMS_CLOCK_*} identifiers. */
public enum Clock {
    /** {@code LMS_CLOCK_REF}: chip reference clock. */
    REF(0),
    /** {@code LMS_CLOCK_SXR}: receive LO. */
    SXR(1),
    /** {@code LMS_CLOCK_SXT}: transmit LO. */
    SXT(2),
    /** {@code LMS_CLOCK_CGEN}: clock generator. */
    CGEN(3),
    /** {@code LMS_CLOCK_RXTSP}: receive signal processor reference clock. Read-only. */
    RXTSP(4),
    /** {@code LMS_CLOCK_TXTSP}: transmit signal processor reference clock. Read-only. */
    TXTSP(5),
    /**
     * {@code LMS_CLOCK_EXTREF}: external reference clock. Write-only: a positive
     * frequency selects the external reference, if the hardware can switch,
     * and zero selects the internal one.
     */
    EXTREF(6);

    private final int id;

    Clock(int id) {
        this.id = id;
    }

    /** Returns the native identifier. */
    public int id() {
        return id;
    }
}
