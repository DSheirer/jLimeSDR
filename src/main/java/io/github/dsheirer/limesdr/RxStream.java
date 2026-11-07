package io.github.dsheirer.limesdr;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import io.github.dsheirer.limesdr.internal.Native;
import java.lang.foreign.MemorySegment;

/**
 * A receive stream. See {@link LimeStream} for how streams share the device.
 *
 * <p>With several receive streams, read them in turn: LimeSuiteNG fetches a
 * block for all channels when the first one is read, and the others return
 * their part of that block.
 */
public final class RxStream extends LimeStream {
    private long lastTimestamp;

    RxStream(LimeSdrDevice device, StreamConfig config) {
        super(device, Direction.RX, config);
    }

    /**
     * Reads up to {@code sampleCount} I/Q pairs into native memory, waiting at
     * most {@code timeoutMillis} for them.
     *
     * @return the number of I/Q pairs read; 0 if none arrived in time
     */
    public int read(MemorySegment buffer, int sampleCount, int timeoutMillis) throws LimeSdrException {
        checkBuffer(buffer, sampleCount);
        checkTimeout(timeoutMillis);
        synchronized (io) {
            ensureOpen();
            return receive(buffer, sampleCount, timeoutMillis);
        }
    }

    /**
     * Reads 16-bit samples, I, Q, I, Q, ..., for the {@link SampleFormat#I16}
     * and {@link SampleFormat#I12} formats. Reads up to {@code iq.length / 2}
     * I/Q pairs.
     *
     * @return the number of I/Q pairs read; 0 if none arrived in time
     */
    public int read(short[] iq, int timeoutMillis) throws LimeSdrException {
        requireFormat(true);
        checkTimeout(timeoutMillis);
        int samples = iq.length / 2;
        synchronized (io) {
            ensureOpen();
            MemorySegment buffer = scratch((long) samples * config.format().bytesPerSample());
            int read = receive(buffer, samples, timeoutMillis);
            MemorySegment.copy(buffer, JAVA_SHORT, 0, iq, 0, read * 2);
            return read;
        }
    }

    /**
     * Reads float samples, I, Q, I, Q, ..., for the {@link SampleFormat#F32}
     * format. Reads up to {@code iq.length / 2} I/Q pairs.
     *
     * @return the number of I/Q pairs read; 0 if none arrived in time
     */
    public int read(float[] iq, int timeoutMillis) throws LimeSdrException {
        requireFormat(false);
        checkTimeout(timeoutMillis);
        int samples = iq.length / 2;
        synchronized (io) {
            ensureOpen();
            MemorySegment buffer = scratch((long) samples * config.format().bytesPerSample());
            int read = receive(buffer, samples, timeoutMillis);
            MemorySegment.copy(buffer, JAVA_FLOAT, 0, iq, 0, read * 2);
            return read;
        }
    }

    /**
     * Returns the hardware timestamp of the first sample of the last read: a
     * sample counter, so consecutive reads without a gap differ by the number of
     * samples read.
     */
    public long lastTimestamp() {
        synchronized (io) {
            return lastTimestamp;
        }
    }

    // Holding io. Calls the handle directly: this runs for every block.
    int receive(MemorySegment buffer, int sampleCount, int timeoutMillis) throws LimeSdrException {
        LimeSdr.clearError();
        int result;
        try {
            result = (int) Native.RECV_STREAM.invokeExact(stream, buffer, (long) sampleCount, meta, timeoutMillis);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
        if (result < 0) {
            throw LimeSdr.failure("LMS_RecvStream", result);
        }
        if (result > 0) {
            lastTimestamp = meta.get(JAVA_LONG, 0);
        }
        return result;
    }
}
