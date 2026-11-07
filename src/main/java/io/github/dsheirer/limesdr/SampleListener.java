package io.github.dsheirer.limesdr;

/**
 * Receives samples from {@link LimeSdrDevice#startRx}.
 *
 * <p>Runs on a thread of its own that reads from the device, so slow work here
 * makes the device's FIFO overflow: copy the samples and hand them to another
 * thread. It may change device settings, such as the frequency, but must not
 * call {@code startRx}, {@code stopRx} or {@code close}; those throw
 * {@link IllegalStateException} here. Return false to stop instead. If it
 * throws, receiving stops and the exception is available from
 * {@link LimeSdrDevice#receiveFailure()}.
 */
@FunctionalInterface
public interface SampleListener {
    /**
     * Handles a block of samples.
     *
     * @return true to keep receiving, false to stop
     */
    boolean onSamples(SampleBuffer buffer);
}
