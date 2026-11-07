package io.github.dsheirer.limesdr;

/**
 * Options of a transmit write, {@code lms_stream_meta_t}.
 *
 * @param timestamp hardware sample counter value at which to send the first sample
 * @param waitForTimestamp hold the samples until {@code timestamp}; otherwise send them at once
 * @param flushPartialPacket send the samples even if they do not fill a packet, to end a burst
 */
public record TxMetadata(long timestamp, boolean waitForTimestamp, boolean flushPartialPacket) {
    /** Send as soon as possible, without flushing. */
    public static final TxMetadata NONE = new TxMetadata(0, false, false);

    /** Send at a hardware timestamp. */
    public static TxMetadata at(long timestamp) {
        return new TxMetadata(timestamp, true, false);
    }

    /** Returns a copy that flushes a partial packet, ending a burst. */
    public TxMetadata withFlush() {
        return new TxMetadata(timestamp, waitForTimestamp, true);
    }
}
