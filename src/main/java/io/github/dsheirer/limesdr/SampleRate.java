package io.github.dsheirer.limesdr;

/**
 * Sample rates of a channel, from {@code LMS_GetSampleRate}.
 *
 * @param host complex samples per second exchanged with the host
 * @param rf sample rate of the ADC or DAC, the host rate times the oversampling ratio
 */
public record SampleRate(double host, double rf) {
}
