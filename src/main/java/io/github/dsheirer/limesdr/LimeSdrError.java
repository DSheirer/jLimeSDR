package io.github.dsheirer.limesdr;

/** Status codes of LimeSuiteNG functions, its {@code lime::OpStatus}. */
public enum LimeSdrError {
    /** {@code Success}. */
    SUCCESS(0),
    /** {@code Error}: a general failure, which most legacy functions report. */
    ERROR(-1),
    /** {@code NotImplemented}. */
    NOT_IMPLEMENTED(-2),
    /** {@code IOFailure}. */
    IO_FAILURE(-3),
    /** {@code InvalidValue}. */
    INVALID_VALUE(-4),
    /** {@code FileNotFound}. */
    FILE_NOT_FOUND(-5),
    /** {@code OutOfRange}. */
    OUT_OF_RANGE(-6),
    /** {@code NotSupported}. */
    NOT_SUPPORTED(-7),
    /** {@code Timeout}. */
    TIMEOUT(-8),
    /** {@code Busy}. */
    BUSY(-9),
    /** {@code Aborted}. */
    ABORTED(-10),
    /** {@code PermissionDenied}. */
    PERMISSION_DENIED(-11),
    /** {@code NotConnected}. */
    NOT_CONNECTED(-12),
    /** A code this version of jLimeSDR does not know. */
    UNKNOWN(Integer.MIN_VALUE);

    private final int code;

    LimeSdrError(int code) {
        this.code = code;
    }

    /** Returns the native value. */
    public int code() {
        return code;
    }

    /** Decodes a native value, returning {@link #UNKNOWN} for an unknown one. */
    public static LimeSdrError fromCode(int code) {
        for (LimeSdrError error : values()) {
            if (error.code == code && error != UNKNOWN) {
                return error;
            }
        }
        return UNKNOWN;
    }
}
