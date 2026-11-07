package io.github.dsheirer.limesdr;

/**
 * Allowed values of a setting, {@code lms_range_t}.
 *
 * @param min lowest value
 * @param max highest value
 * @param step smallest increment, or 0 if any value in the range is allowed
 */
public record Range(double min, double max, double step) {
    /** Returns true if a value lies within the range, inclusive. */
    public boolean contains(double value) {
        return value >= min && value <= max;
    }
}
