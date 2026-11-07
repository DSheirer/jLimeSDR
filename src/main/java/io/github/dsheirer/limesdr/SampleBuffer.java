package io.github.dsheirer.limesdr;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Samples delivered to a {@link SampleListener}: interleaved I/Q pairs, I first,
 * in the stream's {@link SampleFormat}.
 *
 * <p>{@link #samples()} points at a buffer that is reused for the next block. It
 * may be used only on the listener's thread and only until the listener
 * returns; after that, access throws {@link IllegalStateException}. Use one of
 * the copying methods, or {@link MemorySegment#copy}, to keep the data.
 *
 * @param channel channel the samples came from
 * @param format format of the samples
 * @param samples sample memory, {@code sampleCount() * format.bytesPerSample()} bytes long
 * @param timestamp hardware sample counter value of the first sample; consecutive
 *     buffers without a gap differ by their sample count
 */
public record SampleBuffer(int channel, SampleFormat format, MemorySegment samples, long timestamp) {
    /** Returns the number of I/Q pairs. */
    public int sampleCount() {
        return (int) (samples.byteSize() / format.bytesPerSample());
    }

    /** Copies 16-bit samples to a new array: I, Q, I, Q, ... */
    public short[] copyShorts() {
        requireInteger();
        return samples.toArray(ValueLayout.JAVA_SHORT);
    }

    /** Copies 16-bit samples into an array, which must hold at least {@code 2 * sampleCount()} values. */
    public void copyShorts(short[] destination) {
        requireInteger();
        MemorySegment.copy(samples, ValueLayout.JAVA_SHORT, 0, destination, 0, sampleCount() * 2);
    }

    /**
     * Copies the samples to a new array of floats from -1 to 1, scaling
     * integer formats by their full-scale value: I, Q, I, Q, ...
     */
    public float[] copyFloats() {
        float[] result = new float[sampleCount() * 2];
        copyFloats(result);
        return result;
    }

    /**
     * Copies the samples into an array of floats, which must hold at least
     * {@code 2 * sampleCount()} values, scaling integer formats to -1 to 1.
     */
    public void copyFloats(float[] destination) {
        int values = sampleCount() * 2;
        if (destination.length < values) {
            throw new IndexOutOfBoundsException("Destination holds " + destination.length + " floats, not " + values);
        }
        if (format == SampleFormat.F32) {
            MemorySegment.copy(samples, ValueLayout.JAVA_FLOAT, 0, destination, 0, values);
            return;
        }
        float scale = 1f / format.fullScale();
        for (int i = 0; i < values; i++) {
            destination[i] = samples.getAtIndex(ValueLayout.JAVA_SHORT, i) * scale;
        }
    }

    private void requireInteger() {
        if (!format.isInteger()) {
            throw new IllegalStateException("Samples are " + format + ", not 16-bit integers");
        }
    }
}
