package io.github.dsheirer.limesdr;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import io.github.dsheirer.limesdr.internal.Layouts;
import io.github.dsheirer.limesdr.internal.Native;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * A transmit stream. See {@link LimeStream} for how streams share the device.
 *
 * <p>With several transmit streams, write each of them in turn: LimeSuiteNG
 * holds the samples of each channel and sends them once every started stream
 * has samples, using the metadata of the last write.
 */
public final class TxStream extends LimeStream {
    TxStream(LimeSdrDevice device, StreamConfig config) {
        super(device, Direction.TX, config);
    }

    /**
     * Writes {@code sampleCount} I/Q pairs from native memory, waiting at most
     * {@code timeoutMillis} for room in the FIFO.
     *
     * @return the number of I/Q pairs accepted
     */
    public int write(MemorySegment buffer, int sampleCount, TxMetadata metadata, int timeoutMillis)
            throws LimeSdrException {
        checkBuffer(buffer, sampleCount);
        checkTimeout(timeoutMillis);
        Objects.requireNonNull(metadata, "metadata");
        synchronized (io) {
            ensureOpen();
            return send(buffer, sampleCount, metadata, timeoutMillis);
        }
    }

    /**
     * Writes 16-bit samples, I, Q, I, Q, ..., for the {@link SampleFormat#I16}
     * and {@link SampleFormat#I12} formats: {@code iq.length / 2} I/Q pairs.
     *
     * @return the number of I/Q pairs accepted
     */
    public int write(short[] iq, TxMetadata metadata, int timeoutMillis) throws LimeSdrException {
        requireFormat(true);
        checkTimeout(timeoutMillis);
        Objects.requireNonNull(metadata, "metadata");
        int samples = iq.length / 2;
        synchronized (io) {
            ensureOpen();
            MemorySegment buffer = scratch((long) samples * config.format().bytesPerSample());
            MemorySegment.copy(iq, 0, buffer, JAVA_SHORT, 0, samples * 2);
            return send(buffer, samples, metadata, timeoutMillis);
        }
    }

    /**
     * Writes float samples from -1 to 1, I, Q, I, Q, ..., for the
     * {@link SampleFormat#F32} format: {@code iq.length / 2} I/Q pairs.
     *
     * @return the number of I/Q pairs accepted
     */
    public int write(float[] iq, TxMetadata metadata, int timeoutMillis) throws LimeSdrException {
        requireFormat(false);
        checkTimeout(timeoutMillis);
        Objects.requireNonNull(metadata, "metadata");
        int samples = iq.length / 2;
        synchronized (io) {
            ensureOpen();
            MemorySegment buffer = scratch((long) samples * config.format().bytesPerSample());
            MemorySegment.copy(iq, 0, buffer, JAVA_FLOAT, 0, samples * 2);
            return send(buffer, samples, metadata, timeoutMillis);
        }
    }

    // Holding io.
    private int send(MemorySegment buffer, int sampleCount, TxMetadata metadata, int timeoutMillis)
            throws LimeSdrException {
        meta.set(JAVA_LONG, Layouts.offset(Layouts.STREAM_META, "timestamp"), metadata.timestamp());
        meta.set(JAVA_BOOLEAN, Layouts.offset(Layouts.STREAM_META, "waitForTimestamp"), metadata.waitForTimestamp());
        meta.set(JAVA_BOOLEAN, Layouts.offset(Layouts.STREAM_META, "flushPartialPacket"),
            metadata.flushPartialPacket());
        LimeSdr.clearError();
        int result;
        try {
            result = (int) Native.SEND_STREAM.invokeExact(stream, buffer, (long) sampleCount, meta, timeoutMillis);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
        if (result < 0) {
            throw LimeSdr.failure("LMS_SendStream", result);
        }
        return result;
    }
}
