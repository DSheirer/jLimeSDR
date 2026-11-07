package io.github.dsheirer.limesdr;

/**
 * Receives LimeSuiteNG's log messages; see {@link LimeSdr#setLogHandler}.
 *
 * <p>Runs on the thread that produced the message, which may be one of the
 * library's own streaming threads, so it should return quickly. It must not
 * call jLimeSDR. Exceptions it throws are ignored.
 */
@FunctionalInterface
public interface LogHandler {
    /** Handles one message. */
    void log(LogLevel level, String message);
}
