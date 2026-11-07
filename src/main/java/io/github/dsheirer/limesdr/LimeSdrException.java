package io.github.dsheirer.limesdr;

/** Thrown when a LimeSuiteNG function reports an error. */
public class LimeSdrException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int code;

    /**
     * Creates an exception for a failed native function.
     *
     * @param function name of the native function
     * @param code the status it returned
     * @param message the error the library logged while running it, or null
     */
    LimeSdrException(String function, int code, String message) {
        super(function + " failed: " + LimeSdrError.fromCode(code) + " (" + code + ")"
            + (message == null || message.isEmpty() ? "" : ": " + message));
        this.code = code;
    }

    /** Returns the status the native function returned. */
    public int code() {
        return code;
    }

    /** Returns the status as an enum, or {@link LimeSdrError#UNKNOWN} for an unknown code. */
    public LimeSdrError error() {
        return LimeSdrError.fromCode(code);
    }
}
