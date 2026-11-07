package io.github.dsheirer.limesdr;

/**
 * The 16 values of an NCO, read with {@link LimeSdrDevice#ncoFrequencies} or
 * {@link LimeSdrDevice#ncoPhases}.
 *
 * @param values frequencies in Hz in frequency mode, or phase offsets in
 *     degrees in phase mode; {@value LimeSdrDevice#NCO_VALUE_COUNT} of them
 * @param shared the phase offset in degrees in frequency mode, or the
 *     frequency in Hz in phase mode
 */
public record NcoConfiguration(double[] values, double shared) {
    /** Copies the values. */
    public NcoConfiguration {
        values = values.clone();
    }

    /** Returns a copy of the values. */
    @Override
    public double[] values() {
        return values.clone();
    }
}
