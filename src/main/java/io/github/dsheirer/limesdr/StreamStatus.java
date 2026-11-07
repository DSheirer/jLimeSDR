package io.github.dsheirer.limesdr;

/**
 * State of a stream, {@code lms_stream_status_t}. The error counts are those
 * since the previous call to {@code status()}; LimeSuiteNG keeps them per
 * device, so streams of one device share them. LimeSuiteNG does not fill in
 * the structure's {@code active} and {@code sampleRate} members, so they are
 * left out; use {@link LimeStream#isStarted()}.
 *
 * @param fifoFilledCount samples waiting in the FIFO
 * @param fifoSize size of the FIFO in samples
 * @param underrun transmit FIFO underruns
 * @param overrun receive FIFO overruns
 * @param droppedPackets packets the hardware dropped
 * @param linkRate data rate on the link over the last second, bytes per second
 * @param timestamp latest receive timestamp, or last transmit timestamp submitted
 */
public record StreamStatus(int fifoFilledCount, int fifoSize, int underrun, int overrun,
        int droppedPackets, double linkRate, long timestamp) {
}
