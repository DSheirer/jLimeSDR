package io.github.dsheirer.limesdr;

/** Reports progress of {@link LimeSdrDevice#program}. */
@FunctionalInterface
public interface ProgressListener {
    /**
     * Reports progress. Exceptions it throws abort the operation.
     *
     * @param sent bytes written so far
     * @param total bytes to write
     * @param message the library's description of the current step
     * @return true to continue, false to abort
     */
    boolean onProgress(int sent, int total, String message);
}
