package io.github.dsheirer.limesdr;

/**
 * Settings of a stream, the {@code lms_stream_t} passed to {@code LMS_SetupStream}.
 *
 * @param channel channel index
 * @param format format of the samples exchanged with the host
 * @param fifoSize size of the stream's buffer in samples
 * @param linkFormat format on the USB or PCIe link
 * @param alignPhase align channel phases in MIMO mode, {@code LMS_ALIGN_CH_PHASE}; receive only
 * @param throughputVsLatency above 0.5, the stream uses the largest USB or PCIe transfers that the device allows,
 *     for receiving without lost packets when the system is busy; 0.5 and below keep LimeSuiteNG's automatic
 *     transfer size, about 100 microseconds of samples
 */
public record StreamConfig(int channel, SampleFormat format, int fifoSize, LinkFormat linkFormat, boolean alignPhase,
        float throughputVsLatency) {
    /** Default {@link #fifoSize()}, the value LimeSuiteNG's examples use. */
    public static final int DEFAULT_FIFO_SIZE = 1024 * 1024;

    /** Checks the settings. */
    public StreamConfig {
        if (channel < 0) {
            throw new IllegalArgumentException("Invalid channel: " + channel);
        }
        if (format == null || linkFormat == null) {
            throw new NullPointerException("format and linkFormat are required");
        }
        if (fifoSize <= 0) {
            throw new IllegalArgumentException("Invalid FIFO size: " + fifoSize);
        }
        if (!(throughputVsLatency >= 0 && throughputVsLatency <= 1)) {
            throw new IllegalArgumentException("throughputVsLatency must be from 0 to 1: " + throughputVsLatency);
        }
    }

    /** Creates a configuration with the default FIFO size and link format. */
    public static StreamConfig of(int channel, SampleFormat format) {
        return new StreamConfig(channel, format, DEFAULT_FIFO_SIZE, LinkFormat.DEFAULT, false, 0.5f);
    }

    /** Returns a copy with another FIFO size. */
    public StreamConfig withFifoSize(int samples) {
        return new StreamConfig(channel, format, samples, linkFormat, alignPhase, throughputVsLatency);
    }

    /** Returns a copy with another link format. */
    public StreamConfig withLinkFormat(LinkFormat link) {
        return new StreamConfig(channel, format, fifoSize, link, alignPhase, throughputVsLatency);
    }

    /**
     * Returns a copy with another throughput versus latency setting, such as 1 for receiving without lost packets
     * when the system is busy.
     */
    public StreamConfig withThroughputVsLatency(float value) {
        return new StreamConfig(channel, format, fifoSize, linkFormat, alignPhase, value);
    }

    /** Returns a copy that does or does not align channel phases. */
    public StreamConfig withAlignPhase(boolean align) {
        return new StreamConfig(channel, format, fifoSize, linkFormat, align, throughputVsLatency);
    }
}
