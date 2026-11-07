package io.github.dsheirer.limesdr;

/** Severity of a LimeSuiteNG log message, its {@code lime::LogLevel}. */
public enum LogLevel {
    /** The application might not be able to continue. */
    CRITICAL(System.Logger.Level.ERROR),
    /** An operation did not complete. */
    ERROR(System.Logger.Level.ERROR),
    /** An operation completed with an unexpected result. */
    WARNING(System.Logger.Level.WARNING),
    /** An operation completed. */
    INFO(System.Logger.Level.INFO),
    /** Results of intermediate steps. */
    VERBOSE(System.Logger.Level.DEBUG),
    /** Debugging detail. */
    DEBUG(System.Logger.Level.TRACE);

    private final System.Logger.Level systemLevel;

    LogLevel(System.Logger.Level systemLevel) {
        this.systemLevel = systemLevel;
    }

    /** Returns the {@link System.Logger} level that the default handler logs this level at. */
    public System.Logger.Level systemLevel() {
        return systemLevel;
    }

    /** Decodes a native level; unknown values are treated as {@link #DEBUG}. */
    public static LogLevel fromCode(int code) {
        LogLevel[] values = values();
        return code >= 0 && code < values.length ? values[code] : DEBUG;
    }
}
