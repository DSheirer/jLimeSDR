package io.github.dsheirer.limesdr;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.dsheirer.limesdr.internal.Layouts;
import io.github.dsheirer.limesdr.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;

/**
 * A receive or transmit stream of one channel, created by
 * {@link LimeSdrDevice#openRxStream} or {@link LimeSdrDevice#openTxStream}.
 *
 * <p>All streams of a device share one LimeSuiteNG stream. Open every stream
 * you need before starting any of them: the device does not start sending or
 * receiving until every open stream of a direction has been started, and it
 * refuses to open a stream while another one runs.
 *
 * <p>One thread at a time may read or write a stream. The other methods may be
 * called from any thread; {@link #close()} waits for a read or write in
 * progress to finish or time out.
 */
public abstract sealed class LimeStream implements AutoCloseable permits RxStream, TxStream {
    final LimeSdrDevice device;
    final StreamConfig config;
    final Direction direction;
    final Arena arena = Arena.ofShared();
    final MemorySegment stream;
    final MemorySegment meta;
    final Object io = new Object();

    // Guarded by device.lock.
    private boolean started;
    private boolean setUp;
    private volatile boolean closed;

    // Grown on demand by the array methods; guarded by io.
    private MemorySegment scratch = MemorySegment.NULL;

    LimeStream(LimeSdrDevice device, Direction direction, StreamConfig config) {
        this.device = device;
        this.direction = direction;
        this.config = config;
        StructLayout layout = Layouts.STREAM;
        stream = arena.allocate(layout);
        stream.set(JAVA_BOOLEAN, Layouts.offset(layout, "isTx"), direction.isTx());
        int channel = config.channel() | (config.alignPhase() ? LimeSdrDevice.ALIGN_CHANNEL_PHASE : 0);
        stream.set(JAVA_INT, Layouts.offset(layout, "channel"), channel);
        stream.set(JAVA_INT, Layouts.offset(layout, "fifoSize"), config.fifoSize());
        stream.set(JAVA_FLOAT, Layouts.offset(layout, "throughputVsLatency"), config.throughputVsLatency());
        stream.set(JAVA_INT, Layouts.offset(layout, "dataFmt"), config.format().code());
        stream.set(JAVA_INT, Layouts.offset(layout, "linkFmt"), config.linkFormat().code());
        meta = arena.allocate(Layouts.STREAM_META);
    }

    /** Returns the stream's settings. */
    public StreamConfig config() {
        return config;
    }

    /** Returns whether this stream receives or transmits. */
    public Direction direction() {
        return direction;
    }

    /** Returns the channel index. */
    public int channel() {
        return config.channel();
    }

    /**
     * Starts the stream. With several open streams in one direction, the
     * device starts when the last of them is started.
     */
    public void start() throws LimeSdrException {
        synchronized (device.lock) {
            ensureOpen();
            if (!started) {
                LimeSdr.call("LMS_StartStream", Native.START_STREAM, stream);
                started = true;
            }
        }
    }

    /** Stops the stream. The device stops when every stream of the direction is stopped. */
    public void stop() throws LimeSdrException {
        synchronized (device.lock) {
            ensureOpen();
            stopLocked();
        }
    }

    /** Returns true between {@link #start()} and {@link #stop()}. */
    public boolean isStarted() {
        synchronized (device.lock) {
            return started && !closed;
        }
    }

    /** Returns true once the stream is closed. */
    public boolean isClosed() {
        return closed;
    }

    /** Reads the stream's state and the error counts since the last call. */
    public StreamStatus status() throws LimeSdrException {
        synchronized (device.lock) {
            ensureOpen();
            try (Arena call = Arena.ofConfined()) {
                StructLayout layout = Layouts.STREAM_STATUS;
                MemorySegment status = call.allocate(layout);
                LimeSdr.call("LMS_GetStreamStatus", Native.GET_STREAM_STATUS, stream, status);
                return new StreamStatus(
                    status.get(JAVA_INT, Layouts.offset(layout, "fifoFilledCount")),
                    status.get(JAVA_INT, Layouts.offset(layout, "fifoSize")),
                    status.get(JAVA_INT, Layouts.offset(layout, "underrun")),
                    status.get(JAVA_INT, Layouts.offset(layout, "overrun")),
                    status.get(JAVA_INT, Layouts.offset(layout, "droppedPackets")),
                    status.get(JAVA_DOUBLE, Layouts.offset(layout, "linkRate")),
                    status.get(JAVA_LONG, Layouts.offset(layout, "timestamp")));
            }
        }
    }

    /**
     * Stops and releases the stream. Waits for a read or write in progress on
     * another thread to return, which takes at most its timeout.
     */
    @Override
    public void close() throws LimeSdrException {
        synchronized (device.lock) {
            if (closed) {
                return;
            }
            synchronized (io) {
                closed = true;
                try {
                    stopLocked();
                } finally {
                    try {
                        if (setUp) {
                            LimeSdr.call("LMS_DestroyStream", Native.DESTROY_STREAM, device.handle(), stream);
                        }
                    } finally {
                        device.streamClosed(this);
                        arena.close();
                    }
                }
            }
        }
    }

    /** Called by the device, holding its lock, to create the native stream. */
    void setup() throws LimeSdrException {
        LimeSdr.call("LMS_SetupStream", Native.SETUP_STREAM, device.handle(), stream);
        setUp = true;
    }

    /** Releases the stream's memory after a failed setup. */
    void discard() {
        closed = true;
        arena.close();
    }

    private void stopLocked() throws LimeSdrException {
        if (started) {
            started = false;
            LimeSdr.call("LMS_StopStream", Native.STOP_STREAM, stream);
        }
    }

    /** Throws if the stream or its device is closed. Call holding the device lock or {@link #io}. */
    void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Stream is closed");
        }
    }

    /** Checks a caller's buffer for a read or write of {@code sampleCount} samples. */
    void checkBuffer(MemorySegment buffer, int sampleCount) {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("Invalid sample count: " + sampleCount);
        }
        if (!buffer.isNative()) {
            throw new IllegalArgumentException("Buffer must be native memory");
        }
        long needed = (long) sampleCount * config.format().bytesPerSample();
        if (buffer.byteSize() < needed) {
            throw new IndexOutOfBoundsException("Buffer holds " + buffer.byteSize() + " bytes, not " + needed);
        }
    }

    static void checkTimeout(int timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Invalid timeout: " + timeoutMillis);
        }
    }

    /** Returns a native buffer of at least {@code bytes} bytes. Call holding {@link #io}. */
    MemorySegment scratch(long bytes) {
        if (scratch.byteSize() < bytes) {
            scratch = arena.allocate(bytes, 8);
        }
        return scratch.asSlice(0, bytes);
    }

    void requireFormat(boolean integer) {
        if (config.format().isInteger() != integer) {
            throw new IllegalStateException("Stream format is " + config.format()
                + "; use the " + (integer ? "float" : "short") + " methods");
        }
    }
}
